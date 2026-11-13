package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cartoes resumo do painel (gestor): reservas do dia por status com
 * receita esperada, funil da fila de espera e saude da outbox em uma
 * chamada agregada.
 */
class DashboardSummaryApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";

  private String barberId;
  private String serviceId;
  private LocalDate date;
  private LocalDate otherDay;

  @BeforeEach
  void setup() throws Exception {
    barberId = createBarber("Resumo " + UUID.randomUUID());
    serviceId = createService("Corte Resumo " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);
    date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(8);
    otherDay = date.plusDays(1);
    callAuth(
        "PUT",
        "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":"
            + weekdayOf(date)
            + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"},{\"weekday\":"
            + weekdayOf(otherDay)
            + ",\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]");
  }

  @Test
  void summaryAggregatesDayStatusesAndRevenue() throws Exception {
    assertThat(book("10:00").statusCode()).isEqualTo(201);
    assertThat(book("11:00").statusCode()).isEqualTo(201);
    String first = extractId(book("12:00").body());

    // Confirma uma e cancela outra (maquina de estados via dashboard)
    assertThat(
            callAuth("PATCH", "/api/v1/dashboard/appointments/" + first + "/status",
                "{\"status\":\"CONFIRMED\"}").statusCode())
        .isEqualTo(200);
    assertThat(
            callAuth("PATCH", "/api/v1/dashboard/appointments/" + first + "/status",
                "{\"status\":\"CANCELLED\"}").statusCode())
        .isEqualTo(200);

    // Fila de espera (acumulado do tenant): compara antes/depois da inscricao
    long waitingBefore =
        jsonNumber(callAuth("GET", "/api/v1/dashboard/summary?date=" + date, null).body(),
            "waiting");
    assertThat(
            call("POST", "/api/v1/public/waitlist", waitlistBody("14:00")).statusCode())
        .isEqualTo(201);

    var summary = callAuth("GET", "/api/v1/dashboard/summary?date=" + date, null);
    assertThat(summary.statusCode()).isEqualTo(200);
    String body = summary.body();
    // Reservas do dia: 3 no total (1 confirmada->cancelada conta como
    // cancelada; as outras 2 continuam pendentes)
    assertThat(body).contains("\"total\":3");
    assertThat(body).contains("\"pending\":2");
    assertThat(body).contains("\"cancelled\":1");
    // Receita esperada: 2 pendentes x 5000 (a cancelada nao conta)
    assertThat(body).contains("\"expectedRevenueCents\":10000");

    // Outro dia: nenhuma reserva
    var empty = callAuth("GET", "/api/v1/dashboard/summary?date=" + otherDay, null);
    assertThat(empty.statusCode()).isEqualTo(200);
    assertThat(empty.body()).contains("\"total\":0");
    assertThat(empty.body()).contains("\"expectedRevenueCents\":0");

    // Fila de espera acumulada: a inscricao nova aumenta em 1
    assertThat(
            jsonNumber(body, "waiting"))
        .isEqualTo(waitingBefore + 1);
    // Outbox do tenant tem entregas pendentes (eventos das acoes acima)
    assertThat(body).contains("\"outbox\":{\"pending\":");
  }

  // ------------------------------------------------------------------ helpers

  private java.net.http.HttpResponse<String> book(String localTime) throws Exception {
    return callWithHeader(
        "POST",
        "/api/v1/public/appointments",
        "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
            + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + date + "T" + localTime
            + ":00-03:00\",\"customer\":{\"name\":\"Cliente Resumo\",\"phone\":\"+5511950000099\""
            + ",\"consented\":true}}",
        UUID.randomUUID().toString());
  }

  private String waitlistBody(String localTime) {
    return "{\"slug\":\"" + SLUG + "\",\"barberId\":\"" + barberId
        + "\",\"serviceId\":\"" + serviceId + "\",\"startsAt\":\"" + date + "T" + localTime
        + ":00-03:00\",\"customer\":{\"name\":\"Fila Resumo\",\"phone\":\"+5511940000099\""
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

  /** Numero de um campo no corpo do scrape/JSON (sem parser: assertion simples). */
  private static long jsonNumber(String json, String field) {
    String key = "\"" + field + "\":";
    int start = json.indexOf(key) + key.length();
    int end = start;
    while (end < json.length() && Character.isDigit(json.charAt(end))) {
      end++;
    }
    return Long.parseLong(json.substring(start, end));
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
