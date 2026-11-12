package com.barberflow.notification.application;

import com.barberflow.notification.infrastructure.WhatsAppProperties;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * Client HTTP da Cloud API do WhatsApp (spec, secao 12): POST
 * {graph}/{version}/{phone_number_id}/messages com produto "whatsapp" e
 * texto. Resposta 200 traz o ID da mensagem (persistido na outbox para o
 * webhook de status correlacionar). Instanciado por {@link WhatsAppConfig}
 * quando {@code barberflow.whatsapp.enabled=true}.
 */
public class HttpWhatsAppClient implements WhatsAppClient {

  private final WhatsAppProperties properties;
  private final RestClient http;

  public HttpWhatsAppClient(WhatsAppProperties properties) {
    this.properties = properties;
    this.http =
        RestClient.builder()
            .baseUrl("https://graph.facebook.com/" + properties.apiVersion())
            .defaultHeader("Authorization", "Bearer " + properties.accessToken())
            .build();
  }

  @Override
  public String sendText(String phoneE164, String message) {
    var response =
        http
            .post()
            .uri("/" + properties.phoneNumberId() + "/messages")
            .body(
                Map.of(
                    "messaging_product", "whatsapp",
                    "to", phoneE164,
                    "type", "text",
                    "text", Map.of("body", message)))
            .retrieve()
            .toEntity(String.class);

    if (response.getStatusCode().is2xxSuccessful()) {
      // Corpo: {"messages":[{"id":"wamid..."}], ...} — extracao simples.
      String body = response.getBody() == null ? "" : response.getBody();
      int marker = body.indexOf("\"id\":\"");
      if (marker >= 0) {
        int start = marker + "\"id\":\"".length();
        return body.substring(start, body.indexOf('"', start));
      }
      return null;
    }
    throw new IllegalStateException(
        "WhatsApp Cloud API respondeu " + response.getStatusCode().value());
  }
}
