package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.barberflow.notification.application.OutboxEvents;
import com.barberflow.notification.application.OutboxWorker;
import com.barberflow.notification.application.ReminderWorker;
import com.barberflow.notification.infrastructure.OutboxEventJpaEntity;
import com.barberflow.notification.infrastructure.OutboxRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Lembrete 24h (Fase 4): o job varre reservas PENDING/CONFIRMED na janela,
 * agenda a mensagem na outbox de forma IDEMPOTENTE (chave unica por
 * reserva) e o worker entrega pelo template APPOINTMENT_REMINDER.
 */
class ReminderApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";

  @Autowired private ReminderWorker reminders;
  @Autowired private OutboxWorker worker;
  @Autowired private OutboxRepository outbox;

  private String barberId;
  private String serviceId;
  private LocalDate date;

  @BeforeEach
  void setup() throws Exception {
    barberId = createBarber("Lembrete " + UUID.randomUUID());
    serviceId = createService("Corte Lembrete " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);
    date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(3);
    callAuth(
        "PUT",
        "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":"
            + weekdayOf(date)
            + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]");
  }

  @Test
  void reminderIsScheduledIdempotentlyAndDelivered() throws Exception {
    var booked =
        callWithHeader(
            "POST", "/api/v1/public/appointments", bookingBody("10:00"),
            UUID.randomUUID().toString());
    assertThat(booked.statusCode()).isEqualTo(201);
    String appointmentId = extractId(booked.body());
    String key = "appointment-reminder:" + appointmentId;

    // Primeira varredura: evento PENDING na outbox (entrega e do worker)
    reminders.runOnce();
    var event = outbox.findByIdempotencyKey(key);
    assertThat(event).isPresent();
    assertThat(event.get().getEventType()).isEqualTo(OutboxEvents.APPOINTMENT_REMINDER);
    assertThat(event.get().getStatus()).isEqualTo("PENDING");

    // Reexecucao do job (cron ou outra instancia): idempotente por chave —
    // a varredura e global, entao compare o total antes/depois
    long afterFirst = outbox.countByEventType(OutboxEvents.APPOINTMENT_REMINDER);
    reminders.runOnce();
    assertThat(outbox.countByEventType(OutboxEvents.APPOINTMENT_REMINDER))
        .isEqualTo(afterFirst);

    // Entrega: worker renderiza o template APPOINTMENT_REMINDER e envia
    // (LoggingWhatsAppClient em teste); lote de 20 pode consumir ciclos
    // com eventos antigos, repetir com teto
    OutboxEventJpaEntity delivered = event.get();
    for (int i = 0; i < 50; i++) {
      delivered = outbox.findByIdempotencyKey(key).orElseThrow();
      if ("SENT".equals(delivered.getStatus())) {
        break;
      }
      worker.runOnce();
    }
    assertThat(delivered.getStatus()).isEqualTo("SENT");
    assertThat(delivered.getMessageId()).startsWith("disabled-");
  }

  // ------------------------------------------------------------------ helpers

  private String bookingBody(String localTime) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + date + "T" + localTime
        + ":00-03:00\",\"customer\":{\"name\":\"Cliente Lembrete\",\"phone\":\"+5511930000001\""
        + ",\"consented\":true}}";
  }

  private String createBarber(String displayName) throws Exception {
    var response = callAuth("POST", "/api/v1/dashboard/barbers",
        "{\"displayName\":\"" + displayName + "\"}");
    assertThat(response.statusCode()).isEqualTo(201);
    return extractId(response.body());
  }

  private String createService(String name, int durationMinutes) throws Exception {
    var response = callAuth("POST", "/api/v1/dashboard/services",
        "{\"name\":\"" + name + "\",\"durationMinutes\":" + durationMinutes
            + ",\"priceCents\":5000}");
    assertThat(response.statusCode()).isEqualTo(201);
    return extractId(response.body());
  }

  private void enableService(String barberId, String serviceId) throws Exception {
    var response = callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/services",
        "{\"serviceIds\":[\"" + serviceId + "\"]}");
    assertThat(response.statusCode()).isEqualTo(200);
  }

  /** 0=domingo ... 6=sabado (DOW do PostgreSQL). */
  private static int weekdayOf(LocalDate date) {
    return date.getDayOfWeek().getValue() % 7;
  }

  /** POST com header extra (Idempotency-Key obrigatoria na criacao de reserva). */
  private static java.net.http.HttpResponse<String> callWithHeader(
      String method, String path, String jsonBody, String idempotencyKey) throws Exception {
    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + PORT + path))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", idempotencyKey)
            .method(
                method,
                java.net.http.HttpRequest.BodyPublishers.ofString(
                    jsonBody, java.nio.charset.StandardCharsets.UTF_8))
            .build();
    return java.net.http.HttpClient.newHttpClient()
        .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
  }
}
