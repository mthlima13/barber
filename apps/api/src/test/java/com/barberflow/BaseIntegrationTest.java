package com.barberflow;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
    properties = {
      "server.port=8183",
      "spring.security.user.name=test",
      "spring.security.user.password=test"
    })
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
  }

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  /** Requisicao HTTP com autenticacao basica do usuario de teste. */
  static HttpResponse<String> call(String method, String path, String jsonBody) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + path))
            .header("Authorization", basicAuth())
            .header("Content-Type", "application/json")
            .method(
                method,
                jsonBody == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  static String basicAuth() {
    String encoded =
        Base64.getEncoder().encodeToString("test:test".getBytes(StandardCharsets.UTF_8));
    return "Basic " + encoded;
  }
}
