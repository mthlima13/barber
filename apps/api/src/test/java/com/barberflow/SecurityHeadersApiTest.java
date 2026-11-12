package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Hardening (spec, linha 216): headers de seguranca em toda resposta da
 * API — CSP, nosniff, anti-clickjacking, referrer-policy e no-cache.
 */
class SecurityHeadersApiTest extends BaseIntegrationTest {

  private static final String SLUG = "barbearia-piloto";

  @Test
  void publicResponsesCarrySecurityHeaders() throws Exception {
    var response = call("GET", "/api/v1/public/barbershops/" + SLUG, null);

    assertThat(response.statusCode()).isEqualTo(200);
    // A API serve JSON: nada deve embutir ou enquadrar a origem
    assertThat(response.headers().firstValue("Content-Security-Policy"))
        .hasValue("default-src 'none'; frame-ancestors 'none'");
    assertThat(response.headers().firstValue("X-Content-Type-Options"))
        .hasValue("nosniff");
    assertThat(response.headers().firstValue("X-Frame-Options"))
        .hasValue("DENY");
    assertThat(response.headers().firstValue("Referrer-Policy"))
        .hasValue("strict-origin-when-cross-origin");
    // Resposta de API nao e cacheavel por proxies/compartilhamentos
    assertThat(response.headers().firstValue("Cache-Control"))
        .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
  }

  @Test
  void errorResponsesCarrySecurityHeaders() throws Exception {
    var response = call("GET", "/api/v1/public/barbershops/inexistente", null);

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue("X-Content-Type-Options"))
        .hasValue("nosniff");
    assertThat(response.headers().firstValue("Content-Security-Policy"))
        .isPresent();
  }
}
