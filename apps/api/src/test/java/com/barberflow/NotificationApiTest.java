package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Fase 2: outbox de notificacoes e auditoria (spec, secoes 4, 6 e 9) —
 * eventos gravados na MESMA transacao da operacao de negocio; envio real
 * pelo worker na Fase 4.
 */
class NotificationApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";
  private static final String PHONE = "+5511970000001";

  private String barberId;
  private String serviceId;
  private LocalDate date;

  @BeforeEach
  void setupProfessionals() throws Exception {
    barberId = createBarber("Outbox " + UUID.randomUUID());
    serviceId = createService("Corte Outbox " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);
    date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(3);
    callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":" + weekdayOf(date) + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]");
  }

  private String slotIso(String localTime) {
    return date + "T" + localTime + ":00-03:00";
  }

  private String bookBody(String localTime) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + slotIso(localTime)
        + "\",\"customer\":{\"name\":\"Cliente Outbox\",\"phone\":\"" + PHONE + "\",\"consented\":true}}";
  }

  private java.net.http.HttpResponse<String> book(String localTime) throws Exception {
    // Idempotency-Key obrigatoria (spec, linha 149)
    return callWithHeader(
        "POST", "/api/v1/public/appointments", bookBody(localTime), UUID.randomUUID().toString());
  }

  @Test
  void appointmentCreatedEmitsOutboxEventInSameTransaction() throws Exception {
    var booked = book("10:00");
    assertThat(booked.statusCode()).isEqualTo(201);
    String appointmentId = extractId(booked.body());

    var outbox = callAuth("GET", "/api/v1/dashboard/outbox", null);

    assertThat(outbox.statusCode()).isEqualTo(200);
    assertThat(outbox.body()).contains("\"eventType\":\"APPOINTMENT_CREATED\"");
    assertThat(outbox.body()).contains(appointmentId);
  }

  @Test
  void idempotentReplayDoesNotDuplicateEvent() throws Exception {
    String idempotencyKey = "idem-" + UUID.randomUUID();
    callWithHeader("POST", "/api/v1/public/appointments", bookBody("11:00"), idempotencyKey);

    // Replay com a mesma chave: mesma reserva, NAO gera segundo evento
    var replay = callWithHeader(
        "POST", "/api/v1/public/appointments", bookBody("11:00"), idempotencyKey);
    assertThat(replay.statusCode()).isEqualTo(200);

    var outbox = callAuth("GET", "/api/v1/dashboard/outbox", null);

    assertThat(countOccurrences(outbox.body(), "appointment-created:" + idempotencyKey))
        .isEqualTo(1);
  }

  @Test
  void cancellationEmitsCancelledEvent() throws Exception {
    var booked = book("12:00");
    String appointmentId = extractId(booked.body());
    String manageToken = extractJson(booked.body(), "manageToken");
    var cancel = callWith(
        manageToken, "POST", "/api/v1/customer/appointments/" + appointmentId + "/cancel", null);
    assertThat(cancel.statusCode()).isEqualTo(200);

    var outbox = callAuth("GET", "/api/v1/dashboard/outbox", null);

    assertThat(outbox.body()).contains("\"eventType\":\"APPOINTMENT_CANCELLED\"");
    assertThat(outbox.body()).contains(appointmentId);
  }

  @Test
  void waitlistOfferEmitsSlotAvailableEvent() throws Exception {
    var joined = call("POST", "/api/v1/public/waitlist", "{\"slug\":\"" + SLUG
        + "\",\"barberId\":\"" + barberId + "\",\"serviceId\":\"" + serviceId
        + "\",\"startsAt\":\"" + slotIso("13:00")
        + "\",\"customer\":{\"name\":\"Cliente Fila\",\"phone\":\"+5511980000001\",\"consented\":true}}");
    assertThat(joined.statusCode()).isEqualTo(201);
    String entryId = extractId(joined.body());

    // Cancelamento da reserva dispara process() na mesma transacao
    var booked = book("13:00");
    String appointmentId = extractId(booked.body());
    String manageToken = extractJson(booked.body(), "manageToken");
    callWith(manageToken, "POST",
        "/api/v1/customer/appointments/" + appointmentId + "/cancel", null);

    var outbox = callAuth("GET", "/api/v1/dashboard/outbox", null);

    assertThat(outbox.body()).contains("\"eventType\":\"WAITLIST_SLOT_AVAILABLE\"");
    assertThat(outbox.body()).contains(entryId);
  }

  @Test
  void managerStatusChangeIsAudited() throws Exception {
    var booked = book("14:00");
    String appointmentId = extractId(booked.body());
    var confirmed = callAuth("PATCH",
        "/api/v1/dashboard/appointments/" + appointmentId + "/status",
        "{\"status\":\"CONFIRMED\"}");
    assertThat(confirmed.statusCode()).isEqualTo(200);

    var audit = callAuth("GET", "/api/v1/dashboard/audit", null);

    assertThat(audit.statusCode()).isEqualTo(200);
    assertThat(audit.body()).contains("\"action\":\"APPOINTMENT_CONFIRMED\"");
    assertThat(audit.body()).contains("\"entityType\":\"APPOINTMENT\"");
    assertThat(audit.body()).contains(appointmentId);
    // Autor registrado (gestor da sessao)
    assertThat(audit.body()).contains("\"actorId\":");
  }

  @Test
  void dashboardEndpointsRequireAuth() throws Exception {
    var outbox = call("GET", "/api/v1/dashboard/outbox", null);
    var audit = call("GET", "/api/v1/dashboard/audit", null);

    assertThat(outbox.statusCode()).isEqualTo(401);
    assertThat(audit.statusCode()).isEqualTo(401);
  }

  // ------------------------------------------------------------------ helpers

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int index = 0;
    while ((index = haystack.indexOf(needle, index)) != -1) {
      count++;
      index += needle.length();
    }
    return count;
  }

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

  private static String extractJson(String json, String field) {
    int start = json.indexOf("\"" + field + "\":\"") + ("\"" + field + "\":\"").length();
    return json.substring(start, json.indexOf('"', start));
  }
}
