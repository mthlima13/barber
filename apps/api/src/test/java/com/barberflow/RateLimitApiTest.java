package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

/**
 * Fase 2: rate limit por IP (spec, secao 10; linhas 180-188) — janela
 * deslizante de 60s no filtro de servlet, antes da cadeia de seguranca.
 *
 * <p>Isolamento do bucket: cada teste envia um {@code X-Forwarded-For}
 * unico (o filtro considera o primeiro IP do header, padrao de proxy), os
 * demais testes usam remoteAddr (127.0.0.1) sem o header — nenhum
 * interferencia cruzada. Limite de auth em teste = 15
 * ({@code BaseIntegrationTest}).</p>
 */
class RateLimitApiTest extends BaseIntegrationTest {

  private static final String LOGIN_BODY =
      "{\"email\":\"bruteforce@example.com\",\"password\":\"errado123\"}";

  @Test
  void authExceedingLimitReturns429WithRetryAfter() throws Exception {
    String ip = "203.0.113." + (100 + ThreadLocalRandom.current().nextInt(100));

    // 15 tentativas dentro do limite: cada uma processada pelo login (401)
    for (int i = 0; i < 15; i++) {
      HttpResponse<String> response = postWithForwardedIp("/api/v1/auth/login", ip);
      assertThat(response.statusCode()).isEqualTo(401);
    }

    // 16a: bloqueada pelo filtro antes de chegar ao login
    HttpResponse<String> blocked = postWithForwardedIp("/api/v1/auth/login", ip);

    assertThat(blocked.statusCode()).isEqualTo(429);
    assertThat(blocked.headers().firstValue("Retry-After")).contains("60");
    assertThat(blocked.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
    assertThat(blocked.body()).contains("\"status\":429");
    assertThat(blocked.body()).contains("Muitas requisicoes");
  }

  @Test
  void differentIpsHaveIndependentBuckets() throws Exception {
    String ipA = "203.0.113." + (200 + ThreadLocalRandom.current().nextInt(50));
    String ipB = "203.0.113." + (250 + ThreadLocalRandom.current().nextInt(50));

    // Esgota o bucket do IP A
    for (int i = 0; i < 15; i++) {
      assertThat(postWithForwardedIp("/api/v1/auth/login", ipA).statusCode()).isEqualTo(401);
    }
    assertThat(postWithForwardedIp("/api/v1/auth/login", ipA).statusCode()).isEqualTo(429);

    // IP B (mesma rota, mesmo momento) nao e afetado
    assertThat(postWithForwardedIp("/api/v1/auth/login", ipB).statusCode()).isEqualTo(401);
  }

  @Test
  void publicRoutesAreNotLimitedByAuthBucket() throws Exception {
    // Leitura publica usa bucket proprio (300/min em teste): 16 requisicoes
    // seguidas nunca tocam o limite de auth (filtro roda antes do
    // controller; a rota pode responder 400 sem params — nunca 429)
    for (int i = 0; i < 16; i++) {
      HttpResponse<String> response =
          getWithForwardedIp(
              "/api/v1/public/barbershops/barbearia-piloto/availability"
                  + "?date=" + java.time.LocalDate.now(),
              "203.0.113.90");
      assertThat(response.statusCode()).isNotEqualTo(429);
    }
  }

  @Test
  void customerLinkSafeRoutesAreRateLimited() throws Exception {
    // Link seguro sem token: 401 do security, mas contado pelo filtro
    // (roda antes da cadeia) — hammering de token estoura o bucket de
    // escrita publica (150/min em teste), IP proprio via X-Forwarded-For
    String ip = "203.0.113." + (300 + ThreadLocalRandom.current().nextInt(50));
    HttpResponse<String> last = null;
    boolean limited = false;
    for (int i = 0; i < 200; i++) {
      last =
          getWithForwardedIp(
              "/api/v1/customer/appointments/" + java.util.UUID.randomUUID(), ip);
      if (last.statusCode() == 429) {
        limited = true;
        break;
      }
      assertThat(last.statusCode()).isEqualTo(401);
    }
    assertThat(limited).isTrue();
    assertThat(last.body()).contains("Muitas requisicoes");
  }

  // ------------------------------------------------------------------ helpers

  private static HttpResponse<String> postWithForwardedIp(String path, String ip)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + path))
            .header("Content-Type", "application/json")
            .header("X-Forwarded-For", ip)
            .POST(HttpRequest.BodyPublishers.ofString(LOGIN_BODY, StandardCharsets.UTF_8))
            .build();
    return HttpClient.newHttpClient()
        .send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> getWithForwardedIp(String path, String ip)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + path))
            .header("X-Forwarded-For", ip)
            .GET()
            .build();
    return HttpClient.newHttpClient()
        .send(request, HttpResponse.BodyHandlers.ofString());
  }
}
