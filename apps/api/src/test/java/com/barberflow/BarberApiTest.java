package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Fase 1: CRUD de profissionais e habilitacao de servicos (spec, secao 10).
 *
 * <p>Todas as chamadas usam o token do gestor de teste (bootstrap/login da
 * base); o tenant vem do token (spec, secao 5).</p>
 */
class BarberApiTest extends BaseIntegrationTest {

  private String barberJson(String displayName) {
    return """
        {"displayName":"%s"}
        """
        .formatted(displayName);
  }

  private String createService(String name) throws Exception {
    var response =
        callAuth(
            "POST",
            "/api/v1/dashboard/services",
            """
            {"name":"%s","durationMinutes":30,"priceCents":5000}
            """
                .formatted(name));
    assertThat(response.statusCode()).isEqualTo(201);
    int start = response.body().indexOf("\"id\":\"") + 6;
    return response.body().substring(start, response.body().indexOf('"', start));
  }

  private String createBarber(String displayName) throws Exception {
    var response = callAuth("POST", "/api/v1/dashboard/barbers", barberJson(displayName));
    assertThat(response.statusCode()).isEqualTo(201);
    return response.body().substring(
        response.body().indexOf("\"id\":\"") + 6,
        response.body().indexOf('"', response.body().indexOf("\"id\":\"") + 6));
  }

  @Test
  void createReturns201AndActiveByDefault() throws Exception {
    var response = callAuth("POST", "/api/v1/dashboard/barbers", barberJson("Joao " + UUID.randomUUID()));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.headers().firstValue("Location")).isPresent();
    assertThat(response.body()).contains("\"active\":true");
    assertThat(response.body()).contains("\"userId\":null");
  }

  @Test
  void createWithUnknownUserReturns400() throws Exception {
    var response =
        callAuth(
            "POST",
            "/api/v1/dashboard/barbers",
            """
            {"displayName":"Fulano","userId":"%s"}
            """
                .formatted(UUID.randomUUID()));

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void listReturnsCreatedBarbers() throws Exception {
    callAuth("POST", "/api/v1/dashboard/barbers", barberJson("Maria " + UUID.randomUUID()));

    var response = callAuth("GET", "/api/v1/dashboard/barbers", null);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"displayName\"");
  }

  @Test
  void patchUpdatesDisplayName() throws Exception {
    String id = createBarber("Nome " + UUID.randomUUID());

    var response =
        callAuth(
            "PATCH",
            "/api/v1/dashboard/barbers/" + id,
            """
            {"displayName":"Renomeado %s"}
            """
                .formatted(UUID.randomUUID()));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("Renomeado");
  }

  @Test
  void deleteSoftDisablesBarber() throws Exception {
    String id = createBarber("Sai " + UUID.randomUUID());

    var deleted = callAuth("DELETE", "/api/v1/dashboard/barbers/" + id, null);
    assertThat(deleted.statusCode()).isEqualTo(204);

    var after = callAuth("GET", "/api/v1/dashboard/barbers/" + id, null);
    assertThat(after.statusCode()).isEqualTo(200);
    assertThat(after.body()).contains("\"active\":false");
  }

  @Test
  void enableAndListServicesForBarber() throws Exception {
    String barberId = createBarber("Prof " + UUID.randomUUID());
    String s1 = createService("Corte " + UUID.randomUUID());
    String s2 = createService("Barba " + UUID.randomUUID());

    var put =
        callAuth(
            "PUT",
            "/api/v1/dashboard/barbers/" + barberId + "/services",
            """
            {"serviceIds":["%s","%s"]}
            """
                .formatted(s1, s2));
    assertThat(put.statusCode()).isEqualTo(200);
    assertThat(put.body()).contains("\"durationMinutes\":30");

    var list = callAuth("GET", "/api/v1/dashboard/barbers/" + barberId + "/services", null);
    assertThat(list.statusCode()).isEqualTo(200);
    assertThat(list.body()).contains(s1, s2);

    // PUT vazio limpa a habilitacao
    var clear = callAuth("PUT", "/api/v1/dashboard/barbers/" + barberId + "/services",
        "{\"serviceIds\":[]}");
    assertThat(clear.statusCode()).isEqualTo(200);
    assertThat(clear.body()).isEqualTo("[]");
  }

  @Test
  void enableWithUnknownServiceReturns400() throws Exception {
    String barberId = createBarber("Prof " + UUID.randomUUID());

    var response =
        callAuth(
            "PUT",
            "/api/v1/dashboard/barbers/" + barberId + "/services",
            """
            {"serviceIds":["%s"]}
            """
                .formatted(UUID.randomUUID()));

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void getUnknownBarberReturns404() throws Exception {
    var response = callAuth("GET", "/api/v1/dashboard/barbers/" + UUID.randomUUID(), null);
    assertThat(response.statusCode()).isEqualTo(404);
  }
}
