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
