package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Fase 1: autenticacao da equipe (spec, secoes 10 e 11) — login, bootstrap,
 * /me e protecao do dashboard por papel MANAGER.
 */
class AuthApiTest extends BaseIntegrationTest {

  private static final String GESTOR = "gestor@test.local";
  private static final String SENHA = "senha-segura-123";

  @Test
  void loginWithWrongPasswordReturns401Problem() throws Exception {
    bearer(); // garante o gestor padrao na base antes de testar credenciais
    var response = call("POST", "/api/v1/auth/login",
        "{\"email\":\"" + GESTOR + "\",\"password\":\"errada\"}");

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
  }

  @Test
  void loginWithUnknownEmailReturns401() throws Exception {
    bearer(); // garante o gestor padrao na base
    var response = call("POST", "/api/v1/auth/login",
        "{\"email\":\"nobody@test.local\",\"password\":\"qualquer-123\"}");

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void loginReturnsTokenAndManagerIdentity() throws Exception {
    bearer(); // garante o gestor padrao na base
    var response = call("POST", "/api/v1/auth/login",
        "{\"email\":\"" + GESTOR + "\",\"password\":\"" + SENHA + "\"}");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"tokenType\":\"Bearer\"");
    assertThat(response.body()).contains("\"role\":\"MANAGER\"");
    assertThat(response.body()).contains("\"accessToken\":\"");
  }

  @Test
  void meReturnsAuthenticatedManager() throws Exception {
    bearer(); // garante o gestor padrao na base
    var response = callAuth("GET", "/api/v1/me", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"email\":\"" + GESTOR + "\"");
    assertThat(response.body()).contains("\"role\":\"MANAGER\"");
    assertThat(response.body()).contains("\"tenantId\":\"");
  }

  @Test
  void meWithoutTokenReturns401Problem() throws Exception {
    var response = call("GET", "/api/v1/me", null);

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
  }

  @Test
  void dashboardWithoutTokenReturns401() throws Exception {
    var response = call("GET", "/api/v1/dashboard/services", null);
    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void dashboardWithGarbageTokenReturns401() throws Exception {
    var response = callWith("token-invalido", "GET", "/api/v1/dashboard/services", null);
    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void bootstrapTwiceReturns409() throws Exception {
    // O gestor padrao da base ja existe (criado por bearer()); segunda
    // tentativa de bootstrap no mesmo tenant responde 409.
    bearer();
    var response = call("POST", "/api/v1/auth/bootstrap",
        "{\"name\":\"Outro Gestor\",\"email\":\"outro@test.local\",\"password\":\"senha-segura-456\"}");

    assertThat(response.statusCode()).isEqualTo(409);
  }

  @Test
  void bootstrapWithShortPasswordReturns400() throws Exception {
    var response = call("POST", "/api/v1/auth/bootstrap",
        "{\"name\":\"Fulano\",\"email\":\"fulano@test.local\",\"password\":\"curta\"}");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
  }
}
