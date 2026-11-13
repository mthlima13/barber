package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Papel BARBER (spec, linhas 218 e 361): autorizacao por papel E por
 * recurso — o profissional ve e altera APENAS a propria agenda; CRUD da
 * equipe/servicos fica 403.
 */
class BarberAccessApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";

  private String barberAId;
  private String barberBId;
  private String serviceId;
  private LocalDate date;

  @BeforeEach
  void setup() throws Exception {
    barberAId = createBarber("Barbeiro A " + UUID.randomUUID());
    barberBId = createBarber("Barbeiro B " + UUID.randomUUID());
    serviceId = createService("Corte Barb " + UUID.randomUUID(), 30);
    enableService(barberAId, serviceId);
    enableService(barberBId, serviceId);
    date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(6);
    String hours =
        "[{\"weekday\":" + weekdayOf(date) + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]";
    callAuth("PUT", "/api/v1/dashboard/barbers/" + barberAId + "/hours", hours);
    callAuth("PUT", "/api/v1/dashboard/barbers/" + barberBId + "/hours", hours);
  }

  private String book(String barberId, String localTime, String phone) throws Exception {
    var booked = callWithHeader(
        "POST", "/api/v1/public/appointments", bookingBody(barberId, localTime, phone),
        UUID.randomUUID().toString());
    assertThat(booked.statusCode()).isEqualTo(201);
    return extractId(booked.body());
  }

  private String loginAsBarber(String barberId) throws Exception {
    String email = "barber-" + UUID.randomUUID() + "@exemplo.com";
    var created =
        callAuth("POST", "/api/v1/dashboard/barbers/" + barberId + "/credentials",
            "{\"email\":\"" + email + "\",\"password\":\"senha-segura-1\"}");
    assertThat(created.statusCode()).isEqualTo(201);

    var login = call("POST", "/api/v1/auth/login",
        "{\"email\":\"" + email + "\",\"password\":\"senha-segura-1\"}");
    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(login.body()).contains("\"role\":\"BARBER\"");
    return extractJsonField(login.body(), "accessToken");
  }

  @Test
  void barberSeesOnlyOwnAppointments() throws Exception {
    String own = book(barberAId, "10:00", "+5511950000001");
    String colleagues = book(barberBId, "10:00", "+5511950000002");

    String token = loginAsBarber(barberAId);
    var agenda =
        callWith(token, "GET", "/api/v1/dashboard/barber/agenda?date=" + date, null);

    assertThat(agenda.statusCode()).isEqualTo(200);
    // Autorizacao por recurso: so a propria agenda (linha 361)
    assertThat(agenda.body()).contains(own);
    // Nao revela reservas do colega no mesmo dia/horario
    assertThat(agenda.body()).doesNotContain(colleagues);
  }

  @Test
  void barberChangesStatusOfOwnAppointment() throws Exception {
    String own = book(barberAId, "11:00", "+5511950000003");

    String token = loginAsBarber(barberAId);
    var confirmed = callWith(token, "PATCH",
        "/api/v1/dashboard/barber/appointments/" + own + "/status",
        "{\"status\":\"CONFIRMED\"}");

    assertThat(confirmed.statusCode()).isEqualTo(200);
    assertThat(confirmed.body()).contains("\"status\":\"CONFIRMED\"");
  }

  @Test
  void barberCannotChangeAnotherBarbersAppointment() throws Exception {
    String other = book(barberBId, "12:00", "+5511950000004");

    String token = loginAsBarber(barberAId);
    var attempt = callWith(token, "PATCH",
        "/api/v1/dashboard/barber/appointments/" + other + "/status",
        "{\"status\":\"CONFIRMED\"}");

    // Agenda de terceiro: 404, sem revelar existencia (linha 337)
    assertThat(attempt.statusCode()).isEqualTo(404);
  }

  @Test
  void barberCannotAccessManagerDashboard() throws Exception {
    String token = loginAsBarber(barberAId);

    var appointments =
        callWith(token, "GET", "/api/v1/dashboard/appointments?date=" + date, null);
    assertThat(appointments.statusCode()).isEqualTo(403);

    var services = callWith(token, "GET", "/api/v1/dashboard/services", null);
    assertThat(services.statusCode()).isEqualTo(403);

    var barbers = callWith(token, "GET", "/api/v1/dashboard/barbers", null);
    assertThat(barbers.statusCode()).isEqualTo(403);
  }

  @Test
  void userWithoutLinkedProfileIsDenied() throws Exception {
    // Gestor autenticado nao tem perfil de profissional vinculado
    var agenda = callAuth("GET", "/api/v1/dashboard/barber/agenda?date=" + date, null);
    assertThat(agenda.statusCode()).isEqualTo(403);
  }

  @Test
  void credentialsConflictForAlreadyLinkedBarber() throws Exception {
    String token = loginAsBarber(barberAId);

    var again =
        callAuth("POST", "/api/v1/dashboard/barbers/" + barberAId + "/credentials",
            "{\"email\":\"outro-" + UUID.randomUUID() + "@exemplo.com\","
                + "\"password\":\"senha-segura-2\"}");
    assertThat(again.statusCode()).isEqualTo(409);
  }

  // ------------------------------------------------------------------ helpers

  private String bookingBody(String barberId, String localTime, String phone) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + date + "T" + localTime
        + ":00-03:00\",\"customer\":{\"name\":\"Cliente\",\"phone\":\"" + phone
        + "\",\"consented\":true}}";
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

  private static String extractJsonField(String json, String field) {
    int start = json.indexOf("\"" + field + "\":\"") + ("\"" + field + "\":\"").length();
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
