package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Metricas de negocio (spec, linha 293): contadores de reservas e fila de
 * espera e gauges de saude da outbox, expostos em /actuator/prometheus.
 */
class MetricsApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";

  private String barberId;
  private String serviceId;
  private LocalDate date;

  @BeforeEach
  void setup() throws Exception {
    barberId = createBarber("Metrica " + UUID.randomUUID());
    serviceId = createService("Corte Metrica " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);
    date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(7);
    callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":" + weekdayOf(date) + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]");
  }

  @Test
  void prometheusExposesBusinessMetrics() throws Exception {
    // Gera uma reserva real: counter de criacao incrementa na mesma
    // transacao do negocio
    var booked = callWithHeader(
        "POST", "/api/v1/public/appointments", bookingBody("10:00"),
        UUID.randomUUID().toString());
    assertThat(booked.statusCode()).isEqualTo(201);

    var scrape = callAuth("GET", "/actuator/prometheus", null);
    assertThat(scrape.statusCode()).isEqualTo(200);

    String body = scrape.body();
    // Reservas criadas (counter com tag de resultado) e saude da outbox
    // (gauges, sempre presentes)
    assertThat(lineWith(body, "created")).isNotNull();
    assertThat(body).contains("barberflow_outbox_pending");
    assertThat(body).contains("barberflow_outbox_oldest_pending_seconds");
  }

  @Test
  void conflictingBookingIncrementsConflictCounter() throws Exception {
    var first = callWithHeader(
        "POST", "/api/v1/public/appointments", bookingBody("11:00"),
        UUID.randomUUID().toString());
    assertThat(first.statusCode()).isEqualTo(201);

    var second = callWithHeader(
        "POST", "/api/v1/public/appointments", bookingBody("11:00"),
        UUID.randomUUID().toString());
    assertThat(second.statusCode()).isEqualTo(409);

    var scrape = callAuth("GET", "/actuator/prometheus", null);
    assertThat(scrape.statusCode()).isEqualTo(200);
    // O counter de conflito so e criado no 409 real (a fila usa validateFree
    // para "ocupado" normal e nao incrementa)
    assertThat(lineWith(scrape.body(), "conflict")).isNotNull();
  }

  // ------------------------------------------------------------------ helpers

  /** Linha da serie barberflow_appointments_total com o resultado informado. */
  private static String lineWith(String scrape, String outcome) {
    return scrape
        .lines()
        .filter(
            line ->
                line.startsWith("barberflow_appointments_total")
                    && line.contains("outcome=\"" + outcome + "\""))
        .findFirst()
        .orElse(null);
  }

  private String bookingBody(String localTime) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + date + "T" + localTime
        + ":00-03:00\",\"customer\":{\"name\":\"Cliente Metrica\",\"phone\":\"+5511940000001\""
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
