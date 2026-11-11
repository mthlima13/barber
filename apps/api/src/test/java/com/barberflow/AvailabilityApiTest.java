package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Fase 1: motor de disponibilidade (spec, secoes 6, 7 e 10) — expediente,
 * excecoes de agenda e calculo de intervalos livres.
 */
class AvailabilityApiTest extends BaseIntegrationTest {

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

  /** Data de teste: 3 dias no futuro, no fuso da barbearia. */
  private static LocalDate testDate() {
    return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(3);
  }

  /** 0=domingo ... 6=sabado (DOW do PostgreSQL). */
  private static int weekdayOf(LocalDate date) {
    return date.getDayOfWeek().getValue() % 7;
  }

  @Test
  void replaceAndGetHours() throws Exception {
    String barberId = createBarber("Expediente " + UUID.randomUUID());

    var put = callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":" + weekdayOf(testDate()) + ",\"startLocal\":\"09:00\",\"endLocal\":\"12:00\"}]");
    assertThat(put.statusCode()).isEqualTo(200);
    assertThat(put.body()).contains("\"weekday\":" + weekdayOf(testDate()));
    assertThat(put.body()).contains("\"startLocal\":\"09:00:00\"");

    var get = callAuth("GET", "/api/v1/dashboard/barbers/" + barberId + "/hours", null);
    assertThat(get.statusCode()).isEqualTo(200);
    assertThat(get.body()).contains("\"endLocal\":\"12:00:00\"");
  }

  @Test
  void replaceHoursWithEndBeforeStartReturns400() throws Exception {
    String barberId = createBarber("Invalido " + UUID.randomUUID());

    var response = callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":1,\"startLocal\":\"14:00\",\"endLocal\":\"09:00\"}]");

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void availabilityComputesSlotsInsideWindow() throws Exception {
    String barberId = createBarber("Disp " + UUID.randomUUID());
    String serviceId = createService("Corte Disp " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);

    LocalDate date = testDate();
    var put = callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":" + weekdayOf(date) + ",\"startLocal\":\"09:00\",\"endLocal\":\"10:00\"}]");
    assertThat(put.statusCode()).isEqualTo(200);

    var response = call("GET", "/api/v1/public/barbershops/barbearia-piloto/availability"
        + "?barberId=" + barberId + "&serviceId=" + serviceId + "&date=" + date, null);

    assertThat(response.statusCode()).isEqualTo(200);
    // Janela 09:00-10:00, servico de 30 min, passo de 15 min: 09:00, 09:15 e 09:30
    assertThat(response.body()).contains("\"durationMinutes\":30");
    assertThat(countSlots(response.body())).isEqualTo(3);
  }

  @Test
  void availabilityEmptyWithoutBusinessHours() throws Exception {
    String barberId = createBarber("SemExpediente " + UUID.randomUUID());
    String serviceId = createService("Corte SE " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);

    var response = call("GET", "/api/v1/public/barbershops/barbearia-piloto/availability"
        + "?barberId=" + barberId + "&serviceId=" + serviceId + "&date=" + testDate(), null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"slots\":[]");
  }

  @Test
  void exceptionBlocksOverlappingSlots() throws Exception {
    String barberId = createBarber("Bloqueio " + UUID.randomUUID());
    String serviceId = createService("Corte Bloq " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);

    LocalDate date = testDate();
    callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":" + weekdayOf(date) + ",\"startLocal\":\"09:00\",\"endLocal\":\"12:00\"}]");

    // Bloqueio das 10:00 as 11:00 no fuso da barbearia (-03:00)
    String dayIso = date.toString();
    var post = callAuth("POST", "/api/v1/dashboard/barbers/" + barberId + "/exceptions",
        "{\"startAt\":\"" + dayIso + "T10:00:00-03:00\",\"endAt\":\""
            + dayIso + "T11:00:00-03:00\",\"kind\":\"BLOCK\"}");
    assertThat(post.statusCode()).isEqualTo(201);

    var response = call("GET", "/api/v1/public/barbershops/barbearia-piloto/availability"
        + "?barberId=" + barberId + "&serviceId=" + serviceId + "&date=" + date, null);
    assertThat(response.statusCode()).isEqualTo(200);

    // Janela 180 min, 30 min de servico, passo 15: 11 slots livres;
    // bloqueio 10:00-11:00 remove 09:45, 10:00, 10:15, 10:30 e 10:45.
    assertThat(countSlots(response.body())).isEqualTo(6);
  }

  @Test
  void deleteExceptionRestoresSlots() throws Exception {
    String barberId = createBarber("Restaura " + UUID.randomUUID());
    String serviceId = createService("Corte Rest " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);

    LocalDate date = testDate();
    String dayIso = date.toString();
    callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/hours",
        "[{\"weekday\":" + weekdayOf(date) + ",\"startLocal\":\"09:00\",\"endLocal\":\"10:00\"}]");

    var created = callAuth("POST", "/api/v1/dashboard/barbers/" + barberId + "/exceptions",
        "{\"startAt\":\"" + dayIso + "T09:00:00-03:00\",\"endAt\":\""
            + dayIso + "T09:30:00-03:00\",\"kind\":\"DAY_OFF\"}");
    assertThat(created.statusCode()).isEqualTo(201);
    String exceptionId = extractId(created.body());

    var blocked = call("GET", "/api/v1/public/barbershops/barbearia-piloto/availability"
        + "?barberId=" + barberId + "&serviceId=" + serviceId + "&date=" + date, null);
    assertThat(countSlots(blocked.body())).isEqualTo(1); // so 09:30

    var deleted = callAuth("DELETE",
        "/api/v1/dashboard/barbers/" + barberId + "/exceptions/" + exceptionId, null);
    assertThat(deleted.statusCode()).isEqualTo(204);

    var restored = call("GET", "/api/v1/public/barbershops/barbearia-piloto/availability"
        + "?barberId=" + barberId + "&serviceId=" + serviceId + "&date=" + date, null);
    assertThat(countSlots(restored.body())).isEqualTo(3);
  }

  @Test
  void availabilityWithDisabledServiceReturns400() throws Exception {
    String barberId = createBarber("SemHab " + UUID.randomUUID());
    String serviceId = createService("Corte SH " + UUID.randomUUID(), 30);

    var response = call("GET", "/api/v1/public/barbershops/barbearia-piloto/availability"
        + "?barberId=" + barberId + "&serviceId=" + serviceId + "&date=" + testDate(), null);

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void availabilityWithUnknownBarberReturns404() throws Exception {
    String serviceId = createService("Corte UB " + UUID.randomUUID(), 30);

    var response = call("GET", "/api/v1/public/barbershops/barbearia-piloto/availability"
        + "?barberId=" + UUID.randomUUID() + "&serviceId=" + serviceId + "&date=" + testDate(),
        null);

    assertThat(response.statusCode()).isEqualTo(404);
  }

  @Test
  void availabilityWithUnknownSlugReturns404() throws Exception {
    String barberId = createBarber("Slug " + UUID.randomUUID());
    String serviceId = createService("Corte SG " + UUID.randomUUID(), 30);
    enableService(barberId, serviceId);

    var response = call("GET", "/api/v1/public/barbershops/barbearia-inexistente/availability"
        + "?barberId=" + barberId + "&serviceId=" + serviceId + "&date=" + testDate(), null);

    assertThat(response.statusCode()).isEqualTo(404);
  }

  private static int countSlots(String json) {
    return json.split("\"startAt\"", -1).length - 1;
  }
}
