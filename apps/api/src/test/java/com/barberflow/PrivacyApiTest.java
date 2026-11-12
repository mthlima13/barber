package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.barberflow.privacy.PrivacyService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * LGPD (spec, linha 223): consentimento obrigatorio na coleta, canal de
 * acesso e de exclusao (anonimizacao) via link seguro da reserva.
 */
class PrivacyApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";
  private static final String PHONE = "+5511960000001";

  @Autowired
  private PrivacyService privacy;

  private String barberId;
  private String serviceId;
  private LocalDate date;

  @BeforeEach
  void setupProfessionals() throws Exception {
    barberId = createBarber("Priv " + UUID.randomUUID());
    serviceId = createService("Corte Priv " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);
    date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(4);
    callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":" + weekdayOf(date) + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]");
  }

  private String slotIso(String localTime) {
    return date + "T" + localTime + ":00-03:00";
  }

  private String bookBody(String localTime, boolean consented) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + slotIso(localTime)
        + "\",\"customer\":{\"name\":\"Cliente Priv\",\"phone\":\"" + PHONE
        + "\",\"consented\":" + consented + "}}";
  }

  @Test
  void bookingWithoutConsentIsRejected() throws Exception {
    var response = callWithHeader(
        "POST", "/api/v1/public/appointments", bookBody("10:00", false),
        UUID.randomUUID().toString());

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("politica de privacidade");
  }

  @Test
  void reportShowsCollectedDataAndConsent() throws Exception {
    var booked = callWithHeader(
        "POST", "/api/v1/public/appointments", bookBody("10:00", true),
        UUID.randomUUID().toString());
    assertThat(booked.statusCode()).isEqualTo(201);

    String appointmentId = extractId(booked.body());
    String manageToken = extractManageToken(booked.body());

    var report = callWith(manageToken, "GET",
        "/api/v1/customer/appointments/" + appointmentId + "/privacy", null);

    assertThat(report.statusCode()).isEqualTo(200);
    // Acesso ao proprio dado (art. 18): nome, telefone e consentimento
    assertThat(report.body()).contains("\"name\":\"Cliente Priv\"");
    assertThat(report.body()).contains("\"phone\":\"" + PHONE + "\"");
    assertThat(report.body()).contains("\"consentSource\":\"web-booking\"");
    assertThat(report.body()).contains("\"privacyPolicyVersion\":\"1.0\"");
    assertThat(report.body()).contains("\"consentAt\":");
    // A reserva aparece no relatorio
    assertThat(report.body()).contains(appointmentId);
  }

  @Test
  void reportRejectsMissingOrForeignToken() throws Exception {
    var booked = callWithHeader(
        "POST", "/api/v1/public/appointments", bookBody("10:00", true),
        UUID.randomUUID().toString());
    assertThat(booked.statusCode()).isEqualTo(201);
    String appointmentId = extractId(booked.body());
    String manageToken = extractManageToken(booked.body());

    // Sem token: acesso negado
    var missing = call("GET",
        "/api/v1/customer/appointments/" + appointmentId + "/privacy", null);
    assertThat(missing.statusCode()).isIn(401, 403);

    // Token de outra reserva: nao revela dados (vira 404)
    var other = callWithHeader(
        "POST", "/api/v1/public/appointments", bookBody("10:30", true),
        UUID.randomUUID().toString());
    assertThat(other.statusCode()).isEqualTo(201);
    String otherToken = extractManageToken(other.body());
    var foreign = callWith(otherToken, "GET",
        "/api/v1/customer/appointments/" + appointmentId + "/privacy", null);
    assertThat(foreign.statusCode()).isEqualTo(404);
  }

  @Test
  void anonymizeReplacesPersonalDataAndCancelsWaitlist() throws Exception {
    // Reserva + inscricao na lista de espera do MESMO titular
    var booked = callWithHeader(
        "POST", "/api/v1/public/appointments", bookBody("10:00", true),
        UUID.randomUUID().toString());
    assertThat(booked.statusCode()).isEqualTo(201);
    String appointmentId = extractId(booked.body());
    String manageToken = extractManageToken(booked.body());

    var joined = call("POST", "/api/v1/public/waitlist", "{\"slug\":\"" + SLUG
        + "\",\"barberId\":\"" + barberId + "\",\"serviceId\":\"" + serviceId
        + "\",\"startsAt\":\"" + slotIso("11:00")
        + "\",\"customer\":{\"name\":\"Cliente Priv\",\"phone\":\"" + PHONE
        + "\",\"consented\":true}}");
    assertThat(joined.statusCode()).isEqualTo(201);

    var deleted = callWith(manageToken, "DELETE",
        "/api/v1/customer/appointments/" + appointmentId + "/privacy", null);
    assertThat(deleted.statusCode()).isEqualTo(200);

    // Dados pessoais substituidos por anonimos (exclusao, art. 18)
    assertThat(deleted.body()).contains("\"name\":\"Cliente (anonimizado)\"");
    assertThat(deleted.body()).contains("\"phone\":\"anon-");
    assertThat(deleted.body()).contains("\"consentAt\":null");
    assertThat(deleted.body()).contains("\"anonymizedAt\":");
    // Inscricao na lista de espera cancelada; reserva permanece (registro)
    assertThat(deleted.body()).contains("\"status\":\"CANCELLED\"");

    // Repeticao e idempotente (anonimizado ja anonimizado)
    var again = callWith(manageToken, "DELETE",
        "/api/v1/customer/appointments/" + appointmentId + "/privacy", null);
    assertThat(again.statusCode()).isEqualTo(200);
    assertThat(again.body()).contains("\"name\":\"Cliente (anonimizado)\"");
  }

  @Test
  void retentionAnonymizesCustomersWithoutRecentActivity() throws Exception {
    var booked = callWithHeader(
        "POST", "/api/v1/public/appointments", bookBody("12:00", true),
        UUID.randomUUID().toString());
    assertThat(booked.statusCode()).isEqualTo(201);
    String appointmentId = extractId(booked.body());
    String manageToken = extractManageToken(booked.body());

    // Corte no futuro, apos a reserva de teste: ela nao conta como
    // atividade recente e o titular cai na retencao
    int anonymized = privacy.anonymizeStale(Instant.now().plus(java.time.Duration.ofDays(30)));
    assertThat(anonymized).isGreaterThanOrEqualTo(1);

    var report = callWith(manageToken, "GET",
        "/api/v1/customer/appointments/" + appointmentId + "/privacy", null);
    assertThat(report.statusCode()).isEqualTo(200);
    assertThat(report.body()).contains("\"name\":\"Cliente (anonimizado)\"");
    assertThat(report.body()).contains("\"anonymizedAt\":");
  }

  // ------------------------------------------------------------------ helpers

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

  private static String extractManageToken(String json) {
    int start = json.indexOf("\"manageToken\":\"") + "\"manageToken\":\"".length();
    return json.substring(start, json.indexOf('"', start));
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
}
