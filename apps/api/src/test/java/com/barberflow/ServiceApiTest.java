package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Fase 1: CRUD de servicos do painel do gestor (spec, secao 10).
 *
 * <p>Todas as chamadas usam o token do gestor de teste (bootstrap/login da
 * base); o tenant vem do token (spec, secao 5).</p>
 */
class ServiceApiTest extends BaseIntegrationTest {

  private String uniqueName() {
    return "Corte " + UUID.randomUUID().toString().substring(0, 8);
  }

  private String serviceJson(String name, int durationMinutes, long priceCents) {
    return """
        {"name":"%s","description":"Corte classico","durationMinutes":%d,"priceCents":%d}
        """
        .formatted(name, durationMinutes, priceCents);
  }

  @Test
  void createReturns201WithLocationAndDefaults() throws Exception {
    var response = callAuth("POST", "/api/v1/dashboard/services",
        serviceJson(uniqueName(), 30, 5000));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.headers().firstValue("Location")).isPresent();
    assertThat(response.body()).contains("\"active\":true");
    assertThat(response.body()).contains("\"durationMinutes\":30");
  }

  @Test
  void createWithInvalidDurationReturns400Problem() throws Exception {
    var response = callAuth("POST", "/api/v1/dashboard/services",
        serviceJson(uniqueName(), 0, 5000));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
  }

  @Test
  void listReturnsCreatedServices() throws Exception {
    callAuth("POST", "/api/v1/dashboard/services", serviceJson(uniqueName(), 30, 5000));

    var response = callAuth("GET", "/api/v1/dashboard/services", null);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"name\"");
  }

  @Test
  void patchUpdatesPrice() throws Exception {
    var created = callAuth("POST", "/api/v1/dashboard/services", serviceJson(uniqueName(), 30, 5000));
    String id = extractId(created.body());

    var response =
        callAuth("PATCH", "/api/v1/dashboard/services/" + id,
            """
            {"priceCents":6500}
            """);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"priceCents\":6500");
    assertThat(response.body()).contains("\"durationMinutes\":30");
  }

  @Test
  void deleteSoftDisablesService() throws Exception {
    var created = callAuth("POST", "/api/v1/dashboard/services", serviceJson(uniqueName(), 45, 8000));
    String id = extractId(created.body());

    var deleted = callAuth("DELETE", "/api/v1/dashboard/services/" + id, null);
    assertThat(deleted.statusCode()).isEqualTo(204);

    var after = callAuth("GET", "/api/v1/dashboard/services/" + id, null);
    assertThat(after.statusCode()).isEqualTo(200);
    assertThat(after.body()).contains("\"active\":false");
  }

  @Test
  void getUnknownIdReturns404() throws Exception {
    var response = callAuth("GET", "/api/v1/dashboard/services/" + UUID.randomUUID(), null);
    assertThat(response.statusCode()).isEqualTo(404);
  }
}
