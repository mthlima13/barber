package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Fase 2: agendamentos (spec, secoes 7, 8 e 9) — criacao revalidada,
 * conflito 409 com alternativas, link seguro do cliente, reagendamento,
 * cancelamento, agenda e transicoes de status.
 */
class AppointmentApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";
  private static final String PHONE = "+5511990000001";

  private String barberId;
  private String serviceId;
  private LocalDate date;

  @org.junit.jupiter.api.BeforeEach
  void setupProfessionals() throws Exception {
    barberId = createBarber("Agenda " + UUID.randomUUID());
    serviceId = createService("Corte Ag " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);
    date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(3);
    callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":" + weekdayOf(date) + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]");
  }

  private String slotIso(String localTime) {
    return date + "T" + localTime + ":00-03:00";
  }

  /** String UTC correspondente a um horario local -03:00 na data de teste. */
  private String utc(String localTime) {
    var t = java.time.LocalTime.parse(localTime).plusHours(3);
    return "T" + t + ":00Z";
  }

  private String body(String startLocal) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + slotIso(startLocal)
        + "\",\"customer\":{\"name\":\"Cliente Teste\",\"phone\":\"" + PHONE + "\"}}";
  }

  private java.net.http.HttpResponse<String> book(String startLocal) throws Exception {
    // Idempotency-Key obrigatoria (spec, linha 149): chave unica por chamada
    return callWithHeader(
        "POST", "/api/v1/public/appointments", body(startLocal), UUID.randomUUID().toString());
  }

  @org.junit.jupiter.api.Test
  void createReturns201WithManageTokenAndPendingStatus() throws Exception {
    var response = book("09:00");

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body()).contains("\"status\":\"PENDING\"");
    assertThat(response.body()).contains("\"manageToken\":\"");
    assertThat(response.body()).contains("\"durationMinutes\":30");
    assertThat(response.headers().firstValue("Location")).isPresent();
  }

  @org.junit.jupiter.api.Test
  void idempotencyKeyReturnsSameAppointment() throws Exception {
    String key = UUID.randomUUID().toString();
    var first =
        callWithHeader("POST", "/api/v1/public/appointments", body("09:15"), key);
    assertThat(first.statusCode()).isEqualTo(201);

    var replay =
        callWithHeader("POST", "/api/v1/public/appointments", body("09:15"), key);

    assertThat(replay.statusCode()).isEqualTo(200);
    String firstId = extractId(first.body());
    String replayId = extractId(replay.body());
    assertThat(replayId).isEqualTo(firstId);
  }

  @org.junit.jupiter.api.Test
  void missingIdempotencyKeyReturns400() throws Exception {
    var response = call("POST", "/api/v1/public/appointments", body("09:30"));

    // Mutacao critica exige chave (spec, linha 149); handler de
    // MissingRequestHeaderException responde problem+json
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
    assertThat(response.body()).contains("Idempotency-Key");
  }

  @org.junit.jupiter.api.Test
  void overlappingReservationReturns409WithAlternatives() throws Exception {
    var first = book("10:00");
    assertThat(first.statusCode()).isEqualTo(201);

    var conflict = book("10:15");

    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(conflict.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
    assertThat(conflict.body()).contains("\"alternativeSlots\"");
    assertThat(conflict.body()).contains(utc("13:00"));
  }

  @org.junit.jupiter.api.Test
  void bookingOutsideBusinessHoursReturns400() throws Exception {
    var response = book("20:00");
    assertThat(response.statusCode()).isEqualTo(400);
  }

  @org.junit.jupiter.api.Test
  void availabilityExcludesBookedSlot() throws Exception {
    book("11:00");

    var response = call("GET", "/api/v1/public/barbershops/" + SLUG + "/availability"
        + "?barberId=" + barberId + "&serviceId=" + serviceId + "&date=" + date, null);

    assertThat(response.statusCode()).isEqualTo(200);
    // 11:00 e 11:15 (locais) ficam ocupados; 11:30 ainda livre.
    // Padrao completo para nao coincidir com endAt de outro slot.
    assertThat(response.body())
        .doesNotContain("\"startAt\":\"" + date + utc("11:00"));
    assertThat(response.body())
        .doesNotContain("\"startAt\":\"" + date + utc("11:15"));
    assertThat(response.body())
        .contains("\"startAt\":\"" + date + utc("11:30"));
  }

  @org.junit.jupiter.api.Test
  void customerCanGetCancelAndStatusChangesWithManageToken() throws Exception {
    var created = book("12:00");
    assertThat(created.statusCode()).isEqualTo(201);
    String appointmentId = extractId(created.body());
    String token = extractManageToken(created.body());

    var get = callWith(token, "GET", "/api/v1/customer/appointments/" + appointmentId, null);
    assertThat(get.statusCode()).isEqualTo(200);
    assertThat(get.body()).contains("\"status\":\"PENDING\"");

    var cancel = callWith(token, "POST",
        "/api/v1/customer/appointments/" + appointmentId + "/cancel", null);
    assertThat(cancel.statusCode()).isEqualTo(200);
    assertThat(cancel.body()).contains("\"status\":\"CANCELLED\"");

    var after = callWith(token, "GET", "/api/v1/customer/appointments/" + appointmentId, null);
    assertThat(after.body()).contains("\"status\":\"CANCELLED\"");
  }

  @org.junit.jupiter.api.Test
  void rescheduleWithManageTokenMovesReservation() throws Exception {
    var created = book("13:00");
    String appointmentId = extractId(created.body());
    String token = extractManageToken(created.body());

    var reschedule = callWith(token, "POST",
        "/api/v1/customer/appointments/" + appointmentId + "/reschedule",
        "{\"startsAt\":\"" + slotIso("14:00") + "\"}");

    assertThat(reschedule.statusCode()).isEqualTo(200);
    assertThat(reschedule.body()).contains(utc("14:00"));
    assertThat(reschedule.body()).contains("\"status\":\"PENDING\"");
  }

  @org.junit.jupiter.api.Test
  void rescheduleToOccupiedSlotReturns409() throws Exception {
    var created = book("15:00");
    String appointmentId = extractId(created.body());
    String token = extractManageToken(created.body());
    var other = book("15:30"); // ocupa 15:30-16:00
    assertThat(other.statusCode()).isEqualTo(201);

    var reschedule = callWith(token, "POST",
        "/api/v1/customer/appointments/" + appointmentId + "/reschedule",
        "{\"startsAt\":\"" + slotIso("15:30") + "\"}");

    assertThat(reschedule.statusCode()).isEqualTo(409);
    assertThat(reschedule.body()).contains("\"alternativeSlots\"");
  }

  @org.junit.jupiter.api.Test
  void customerEndpointsRejectForeignOrInvalidTokens() throws Exception {
    var created = book("16:15");
    String appointmentId = extractId(created.body());

    var withoutToken = call("GET", "/api/v1/customer/appointments/" + appointmentId, null);
    assertThat(withoutToken.statusCode()).isEqualTo(401);

    var managerToken = bearer();
    var wrongRole =
        callWith(managerToken, "GET", "/api/v1/customer/appointments/" + appointmentId, null);
    assertThat(wrongRole.statusCode()).isEqualTo(403);
  }

  @org.junit.jupiter.api.Test
  void dashboardAgendaAndStatusTransitions() throws Exception {
    var created = book("17:00");
    String appointmentId = extractId(created.body());

    var agenda = callAuth("GET", "/api/v1/dashboard/appointments?date=" + date, null);
    assertThat(agenda.statusCode()).isEqualTo(200);
    assertThat(agenda.body()).contains(appointmentId);

    var agendaFiltered =
        callAuth("GET", "/api/v1/dashboard/appointments?date=" + date + "&barberId=" + barberId,
            null);
    assertThat(agendaFiltered.statusCode()).isEqualTo(200);
    assertThat(agendaFiltered.body()).contains(appointmentId);

    var confirm = callAuth("PATCH", "/api/v1/dashboard/appointments/" + appointmentId + "/status",
        "{\"status\":\"CONFIRMED\"}");
    assertThat(confirm.statusCode()).isEqualTo(200);
    assertThat(confirm.body()).contains("\"status\":\"CONFIRMED\"");

    var complete = callAuth("PATCH", "/api/v1/dashboard/appointments/" + appointmentId + "/status",
        "{\"status\":\"COMPLETED\"}");
    assertThat(complete.statusCode()).isEqualTo(200);
    assertThat(complete.body()).contains("\"status\":\"COMPLETED\"");

    var invalid = callAuth("PATCH", "/api/v1/dashboard/appointments/" + appointmentId + "/status",
        "{\"status\":\"CANCELLED\"}");
    assertThat(invalid.statusCode()).isEqualTo(409);
  }

  @org.junit.jupiter.api.Test
  void completedAppointmentFreesTheSlot() throws Exception {
    var created = book("17:30");
    String appointmentId = extractId(created.body());
    // Maquina de estados (spec, secao 8): PENDING -> CONFIRMED -> COMPLETED.
    var confirm = callAuth("PATCH", "/api/v1/dashboard/appointments/" + appointmentId + "/status",
        "{\"status\":\"CONFIRMED\"}");
    assertThat(confirm.statusCode()).isEqualTo(200);
    var complete = callAuth("PATCH", "/api/v1/dashboard/appointments/" + appointmentId + "/status",
        "{\"status\":\"COMPLETED\"}");
    assertThat(complete.statusCode()).isEqualTo(200);

    var next = book("17:30");

    assertThat(next.statusCode()).isEqualTo(201);
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

  private static String extractManageToken(String json) {
    int start = json.indexOf("\"manageToken\":\"") + "\"manageToken\":\"".length();
    return json.substring(start, json.indexOf('"', start));
  }

  /** POST com header extra (Idempotency-Key). */
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
