package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fase 1: CRUD de servicos do painel do gestor (spec, secao 10).
 *
 * <p>O tenant padrao e provisionado diretamente no banco; o fluxo real de
 * autenticacao/autorizacao por papel entra com o modulo auth.</p>
 */
class ServiceApiTest extends BaseIntegrationTest {

  private static final String TENANT_SLUG = "barbearia-piloto";

  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void provisionDefaultTenant() {
    jdbc.update(
        "INSERT INTO tenants (slug, name, timezone, status) VALUES (?, ?, ?, 'ACTIVE')"
            + " ON CONFLICT (slug) DO NOTHING",
        TENANT_SLUG,
        "Barbearia Piloto",
        "America/Sao_Paulo");
  }

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
    var response = call("POST", "/api/v1/dashboard/services",
        serviceJson(uniqueName(), 30, 5000));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.headers().firstValue("Location")).isPresent();
    assertThat(response.body()).contains("\"active\":true");
    assertThat(response.body()).contains("\"durationMinutes\":30");
  }

  @Test
  void createWithInvalidDurationReturns400Problem() throws Exception {
    var response = call("POST", "/api/v1/dashboard/services",
        serviceJson(uniqueName(), 0, 5000));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
  }

  @Test
  void listReturnsCreatedServices() throws Exception {
    call("POST", "/api/v1/dashboard/services", serviceJson(uniqueName(), 30, 5000));

    var response = call("GET", "/api/v1/dashboard/services", null);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"name\"");
  }

  @Test
  void patchUpdatesPrice() throws Exception {
    var created = call("POST", "/api/v1/dashboard/services", serviceJson(uniqueName(), 30, 5000));
    String id = extractId(created.body());

    var response =
        call("PATCH", "/api/v1/dashboard/services/" + id,
            """
            {"priceCents":6500}
            """);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"priceCents\":6500");
    assertThat(response.body()).contains("\"durationMinutes\":30");
  }

  @Test
  void deleteSoftDisablesService() throws Exception {
    var created = call("POST", "/api/v1/dashboard/services", serviceJson(uniqueName(), 45, 8000));
    String id = extractId(created.body());

    var deleted = call("DELETE", "/api/v1/dashboard/services/" + id, null);
    assertThat(deleted.statusCode()).isEqualTo(204);

    var after = call("GET", "/api/v1/dashboard/services/" + id, null);
    assertThat(after.statusCode()).isEqualTo(200);
    assertThat(after.body()).contains("\"active\":false");
  }

  @Test
  void getUnknownIdReturns404() throws Exception {
    var response = call("GET", "/api/v1/dashboard/services/" + UUID.randomUUID(), null);
    assertThat(response.statusCode()).isEqualTo(404);
  }

  private static String extractId(String json) {
    int start = json.indexOf("\"id\":\"") + 6;
    return json.substring(start, json.indexOf('"', start));
  }
}
