package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Fase 0: a aplicacao sobe, aplica migracoes e expoe healthcheck publico. */
class BarberFlowApiApplicationTest extends BaseIntegrationTest {

  @Test
  void contextLoads() {}

  @Test
  void healthcheckIsPublicAndUp() throws Exception {
    var response = call("GET", "/actuator/health", null);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }
}
