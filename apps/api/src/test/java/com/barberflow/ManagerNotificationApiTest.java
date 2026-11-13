package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.barberflow.notification.application.OutboxEvents;
import com.barberflow.notification.application.OutboxWorker;
import com.barberflow.notification.infrastructure.OutboxEventJpaEntity;
import com.barberflow.notification.infrastructure.OutboxRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Notificacoes ao GESTOR (Fase 4): WhatsApp do tenant em manager_phone
 * recebe reserva nova e inscricao na fila via outbox (audience=manager).
 * Sem telefone configurado, nada e agendado.
 */
class ManagerNotificationApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";
  private static final String MANAGER_PHONE = "+5511990000001";

  @Autowired private OutboxRepository outbox;
  @Autowired private OutboxWorker worker;

  private String barberId;
  private String serviceId;
  private LocalDate date;

  @BeforeEach
  void setup() throws Exception {
    barberId = createBarber("Gestor Notif " + UUID.randomUUID());
    serviceId = createService("Corte Notif " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);
    date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(4);
    callAuth(
        "PUT",
        "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":"
            + weekdayOf(date)
            + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]");
  }

  @AfterEach
  void clearManagerPhone() throws Exception {
    callAuth("PATCH", "/api/v1/dashboard/settings", "{\"managerPhone\":null}");
  }

  @Test
  void managerPhoneGatesAndDrivesNotifications() throws Exception {
    // Sem telefone configurado: nenhuma notificacao de gestor
    assertThat(book("10:00").statusCode()).isEqualTo(201);
    assertThat(outbox.countByEventType(OutboxEvents.MANAGER_APPOINTMENT_CREATED))
        .isEqualTo(0);

    // Configuracao: telefone em E.164, invalido rejeitado com 400
    assertThat(
            callAuth("PATCH", "/api/v1/dashboard/settings", "{\"managerPhone\":\"119\"}")
                .statusCode())
        .isEqualTo(400);
    var patched =
        callAuth("PATCH", "/api/v1/dashboard/settings", "{\"managerPhone\":\"" + MANAGER_PHONE + "\"}");
    assertThat(patched.statusCode()).isEqualTo(200);
    var settings = callAuth("GET", "/api/v1/dashboard/settings", null);
    assertThat(settings.statusCode()).isEqualTo(200);
    assertThat(settings.body()).contains(MANAGER_PHONE);

    // Reserva nova => evento do gestor com audience=manager
    String appointmentId = book("11:00").body();
    String appointmentKey = extractId(appointmentId);
    var created = outbox.findByIdempotencyKey("manager-appointment-created:" + appointmentKey);
    assertThat(created).isPresent();
    assertThat(created.get().getEventType())
        .isEqualTo(OutboxEvents.MANAGER_APPOINTMENT_CREATED);
    assertThat(created.get().getPayloadJson()).contains("\"audience\":\"manager\"");

    // Inscricao na fila => evento do gestor
    var joined =
        call("POST", "/api/v1/public/waitlist", waitlistBody("12:00"));
    assertThat(joined.statusCode()).isEqualTo(201);
    var waitlistEvent =
        outbox
            .findByIdempotencyKey("manager-waitlist-joined:" + extractId(joined.body()))
            .orElseThrow();
    assertThat(waitlistEvent.getEventType()).isEqualTo(OutboxEvents.MANAGER_WAITLIST_JOINED);

    // Entrega: telefone resolvido do TENANT (Logging em teste => SENT)
    assertSent("manager-appointment-created:" + appointmentKey);
    assertSent("manager-waitlist-joined:" + extractId(joined.body()));
  }

  @Test
  void managerNotifiedOnCustomerCancelAndReschedule() throws Exception {
    callAuth(
        "PATCH", "/api/v1/dashboard/settings",
        "{\"managerPhone\":\"" + MANAGER_PHONE + "\"}");

    // Cancelamento pelo link seguro: evento de gestor com o slot antigo
    var booked = book("10:00");
    String cancelId = extractId(booked.body());
    String cancelToken = extractJson(booked.body(), "manageToken");
    var cancelled =
        callWith(cancelToken, "POST",
            "/api/v1/customer/appointments/" + cancelId + "/cancel", null);
    assertThat(cancelled.statusCode()).isEqualTo(200);
    var cancelEvent =
        outbox.findByIdempotencyKey("manager-appointment-cancelled:" + cancelId)
            .orElseThrow();
    assertThat(cancelEvent.getEventType())
        .isEqualTo(OutboxEvents.MANAGER_APPOINTMENT_CANCELLED);
    assertThat(cancelEvent.getPayloadJson()).contains("\"audience\":\"manager\"");

    // Remarcacao pelo link seguro: evento com horario anterior e novo
    var booked2 = book("11:00");
    String rescheduleId = extractId(booked2.body());
    String rescheduleToken = extractJson(booked2.body(), "manageToken");
    String newStartsAt = date + "T15:00:00-03:00";
    var rescheduled =
        callWith(rescheduleToken, "POST",
            "/api/v1/customer/appointments/" + rescheduleId + "/reschedule",
            "{\"startsAt\":\"" + newStartsAt + "\"}");
    assertThat(rescheduled.statusCode()).isEqualTo(200);
    // A chave do evento usa o Instant normalizado (UTC) do servico
    String rescheduleKey =
        "manager-appointment-rescheduled:" + rescheduleId + ":"
            + java.time.Instant.parse(newStartsAt);
    var rescheduleEvent = outbox.findByIdempotencyKey(rescheduleKey).orElseThrow();
    assertThat(rescheduleEvent.getEventType())
        .isEqualTo(OutboxEvents.MANAGER_APPOINTMENT_RESCHEDULED);

    // Segunda remarcacao: NOVA mensagem (chave inclui o novo horario)
    String secondStartsAt = date + "T16:00:00-03:00";
    var again =
        callWith(rescheduleToken, "POST",
            "/api/v1/customer/appointments/" + rescheduleId + "/reschedule",
            "{\"startsAt\":\"" + secondStartsAt + "\"}");
    assertThat(again.statusCode()).isEqualTo(200);
    assertThat(outbox.countByEventType(OutboxEvents.MANAGER_APPOINTMENT_RESCHEDULED))
        .isEqualTo(2);

    // Entrega end-to-end dos dois primeiros eventos
    assertSent("manager-appointment-cancelled:" + cancelId);
    assertSent(rescheduleKey);
  }

  // ------------------------------------------------------------------ helpers

  /** Repete runOnce() ate o evento sair; lote de 20 pode consumir ciclos. */
  private void assertSent(String idempotencyKey) {
    OutboxEventJpaEntity event = null;
    for (int i = 0; i < 50; i++) {
      event = outbox.findByIdempotencyKey(idempotencyKey).orElseThrow();
      if ("SENT".equals(event.getStatus())) {
        break;
      }
      try {
        worker.runOnce();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    assertThat(event.getStatus()).isEqualTo("SENT");
    assertThat(event.getMessageId()).startsWith("disabled-");
  }

  private java.net.http.HttpResponse<String> book(String localTime) throws Exception {
    return callWithHeader(
        "POST",
        "/api/v1/public/appointments",
        "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
            + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + date + "T" + localTime
            + ":00-03:00\",\"customer\":{\"name\":\"Cliente Gestor\",\"phone\":\"+5511970000001\""
            + ",\"consented\":true}}",
        UUID.randomUUID().toString());
  }

  private String waitlistBody(String localTime) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + date + "T" + localTime
        + ":00-03:00\",\"customer\":{\"name\":\"Fila Gestor\",\"phone\":\"+5511960000001\""
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

  /** Valor string de um campo JSON de nivel superior (corpos simples de teste). */
  private static String extractJson(String json, String field) {
    String key = "\"" + field + "\":\"";
    int start = json.indexOf(key) + key.length();
    return json.substring(start, json.indexOf('"', start));
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
