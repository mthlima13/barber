package com.barberflow;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integracao: aplicacao completa em porta fixa sobre
 * PostgreSQL real (Testcontainers) com migracoes Flyway aplicadas.
 *
 * <p>O container usa o padrao "singleton": iniciado uma vez por JVM (sem
 * {@code @Container}), para ser compativel com o cache de contextos do Spring
 * Test — cada classe de teste reutiliza o mesmo banco.</p>
 *
 * <p>Requer Docker em execucao.</p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {"server.port=8183"})
public abstract class BaseIntegrationTest {

  static final int PORT = 8183;

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // Rate limit: limites de teste — rotas publicas altos (a suite inteira
    // passa pelo mesmo IP) e auth baixo para o teste de 429 (RateLimitApiTest)
    registry.add("barberflow.ratelimit.public-read-per-minute", () -> "300");
    registry.add("barberflow.ratelimit.public-write-per-minute", () -> "150");
    registry.add("barberflow.ratelimit.auth-per-minute", () -> "15");
    // WhatsApp: webhook verificavel nos testes; worker com polling manual
    // (intervalo de 1h evita corrida do @Scheduled com o runOnce do teste) e
    // backoff zero para esgotar tentativas dentro do teste
    registry.add("barberflow.whatsapp.verify-token", () -> "test-verify-token");
    registry.add("barberflow.whatsapp.app-secret", () -> "test-app-secret");
    registry.add("barberflow.whatsapp.poll-interval-millis", () -> "3600000");
    registry.add("barberflow.whatsapp.first-backoff-seconds", () -> "0");
    // Lembrete 24h: desligado no @Scheduled (job manual no teste) e janela
    // larga para a reserva de +3 dias entrar na varredura
    registry.add("barberflow.reminders.enabled", () -> "false");
    registry.add("barberflow.reminders.window-hours", () -> "720");
    // Expiracao de PENDING: desligada no @Scheduled (job manual no teste) e
    // TTL zero (expira tudo criado antes da varredura)
    registry.add("barberflow.appointment-expiry.enabled", () -> "false");
    registry.add("barberflow.appointment-expiry.pending-ttl-hours", () -> "0");
  }

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  /** Requisicao sem autenticacao. */
  static HttpResponse<String> call(String method, String path, String jsonBody) throws Exception {
    return exchange(method, path, jsonBody, null);
  }

  /** Requisicao com Bearer do gestor de teste (bootstrap/login sob demanda). */
  static HttpResponse<String> callAuth(String method, String path, String jsonBody)
      throws Exception {
    return exchange(method, path, jsonBody, bearer());
  }

  /** Requisicao com token explicito (para testar tokens invalidos). */
  static HttpResponse<String> callWith(String token, String method, String path, String jsonBody)
      throws Exception {
    return exchange(method, path, jsonBody, token);
  }

  private static HttpResponse<String> exchange(
      String method, String path, String jsonBody, String bearerToken) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + path))
            .header("Content-Type", "application/json");
    if (bearerToken != null) {
      builder.header("Authorization", "Bearer " + bearerToken);
    }
    builder.method(
        method,
        jsonBody == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static final Object BEARER_LOCK = new Object();
  private static volatile String cachedBearer;

  /**
   * Token do gestor de teste: faz bootstrap na primeira chamada; se o gestor
   * ja existe (409), faz login. Em cache para nao repetir a cada teste.
   */
  static String bearer() throws Exception {
    synchronized (BEARER_LOCK) {
      if (cachedBearer != null) {
        return cachedBearer;
      }

      // tenant padrao e criado pelo bootstrap; aguarda a app estar de pe
      waitForApplication();

      var bootstrap = exchange(
          "POST",
          "/api/v1/auth/bootstrap",
          "{\"name\":\"Gestor Teste\",\"email\":\"gestor@test.local\","
              + "\"password\":\"senha-segura-123\"}",
          null);
      String token = extractAccessToken(bootstrap.body());
      if (token == null) {
        // 409: gestor ja existe nesta base (ex.: contexto recriado)
        var login = exchange(
            "POST",
            "/api/v1/auth/login",
            "{\"email\":\"gestor@test.local\",\"password\":\"senha-segura-123\"}",
            null);
        token = extractAccessToken(login.body());
      }
      if (token == null) {
        throw new IllegalStateException("Nao foi possivel obter token do gestor de teste");
      }
      cachedBearer = token;
      return token;
    }
  }

  private static void waitForApplication() throws Exception {
    for (int i = 0; i < 120; i++) {
      try {
        var response = call("GET", "/actuator/health", null);
        if (response.statusCode() == 200) {
          return;
        }
      } catch (Exception e) {
        // app ainda subindo
      }
      Thread.sleep(250);
    }
    throw new IllegalStateException("Aplicacao nao ficou pronta a tempo");
  }

  private static String extractAccessToken(String json) {
    if (json == null || !json.contains("\"accessToken\"")) {
      return null;
    }
    int start = json.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
    return json.substring(start, json.indexOf('"', start));
  }

  static String extractId(String json) {
    int start = json.indexOf("\"id\":\"") + 6;
    return json.substring(start, json.indexOf('"', start));
  }
}
