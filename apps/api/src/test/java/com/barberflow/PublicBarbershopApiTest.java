package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Fase 2: catalogo publico da barbearia (spec, linhas 177-179) — a pagina
 * de agendamento descobre servicos e profissionais pelo slug; apenas
 * entidades ativas sao visiveis; slug desconhecido responde 404.
 */
class PublicBarbershopApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";

  @Test
  void barbershopReturnsPublicDataBySlug() throws Exception {
    var response = call("GET", "/api/v1/public/barbershops/" + SLUG, null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"slug\":\"" + SLUG + "\"");
    assertThat(response.body()).contains("\"name\":");
    assertThat(response.body()).contains("\"timezone\":\"America/Sao_Paulo\"");
    // Sem campos administrativos no contrato publico
    assertThat(response.body()).doesNotContain("\"status\":");
    assertThat(response.body()).doesNotContain("\"createdAt\":");
  }

  @Test
  void unknownSlugReturns404() throws Exception {
    var response = call("GET", "/api/v1/public/barbershops/nao-existe-" + UUID.randomUUID(), null);

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
  }

  @Test
  void servicesListOnlyActive() throws Exception {
    String activeName = "Corte Publico " + UUID.randomUUID();
    String removedName = "Servico Removido " + UUID.randomUUID();
    String activeId = createService(activeName, 30);
    String removedId = createService(removedName, 60);
    var delete = callAuth("DELETE", "/api/v1/dashboard/services/" + removedId, null);
    assertThat(delete.statusCode()).isEqualTo(204);

    var response = call("GET", "/api/v1/public/barbershops/" + SLUG + "/services", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains(activeName);
    assertThat(response.body()).contains("\"priceCents\":5000");
    assertThat(response.body()).doesNotContain(removedName);
    // Sem campos administrativos no contrato publico
    assertThat(response.body()).doesNotContain("\"active\":");
    assertThat(response.body()).doesNotContain("\"createdAt\":");
  }

  @Test
  void barbersListOnlyActive() throws Exception {
    String activeName = "Barbeiro Publico " + UUID.randomUUID();
    String removedName = "Barbeiro Removido " + UUID.randomUUID();
    String activeId = createBarber(activeName);
    String removedId = createBarber(removedName);
    var delete = callAuth("DELETE", "/api/v1/dashboard/barbers/" + removedId, null);
    assertThat(delete.statusCode()).isEqualTo(204);

    var response = call("GET", "/api/v1/public/barbershops/" + SLUG + "/barbers", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains(activeName);
    assertThat(response.body()).doesNotContain(removedName);
    // Sem campos internos no contrato publico
    assertThat(response.body()).doesNotContain("\"userId\":");
    assertThat(response.body()).doesNotContain("\"active\":");
  }

  @Test
  void unknownSlugHasNoServicesLeak() throws Exception {
    var response =
        call("GET", "/api/v1/public/barbershops/nao-existe-" + UUID.randomUUID() + "/services",
            null);

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).doesNotContain("priceCents");
  }

  // ------------------------------------------------------------------ helpers

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
}
