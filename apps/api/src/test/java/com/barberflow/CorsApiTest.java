package com.barberflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * CORS do web publico (browser em outra origem chama a API): GET com
 * Origin autorizada recebe Access-Control-Allow-Origin da MESMA origem;
 * origem estranha NAO recebe o header (fetch do browser falha).
 */
class CorsApiTest extends BaseIntegrationTest {

  private static final String PATH =
      "/api/v1/public/barbershops/barbearia-piloto";

  @Test
  void browserOriginGetsCorsHeaderAndUnknownOriginDoesNot() throws Exception {
    var allowed = callWithOrigin(PATH, "http://localhost:3000");
    assertThat(allowed.statusCode()).isEqualTo(200);
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin"))
        .contains("http://localhost:3000");

    var unknown = callWithOrigin(PATH, "http://site-malicioso.example");
    assertThat(unknown.headers().firstValue("Access-Control-Allow-Origin"))
        .isEmpty();
  }

  private java.net.http.HttpResponse<String> callWithOrigin(String path, String origin)
      throws Exception {
    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + PORT + path))
            .header("Origin", origin)
            .GET()
            .build();
    return java.net.http.HttpClient.newHttpClient()
        .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
  }
}
