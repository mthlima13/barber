package com.barberflow.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais do WhatsApp Business Cloud API (spec, secao 12; variaveis em
 * {@code .env.example}). Segredos NUNCA no Git — producao via secret manager.
 */
@ConfigurationProperties(prefix = "barberflow.whatsapp")
public record WhatsAppProperties(
    boolean enabled,
    String phoneNumberId,
    String accessToken,
    String appSecret,
    String verifyToken,
    String apiVersion) {

  public WhatsAppProperties {
    if (apiVersion == null || apiVersion.isBlank()) {
      apiVersion = "v23.0";
    }
  }
}
