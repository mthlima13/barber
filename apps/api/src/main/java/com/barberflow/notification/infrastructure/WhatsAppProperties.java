package com.barberflow.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais do WhatsApp Business Cloud API (spec, secao 12; variaveis em
 * {@code .env.example}). Segredos NUNCA no Git — producao via secret manager.
 * {@code templateLanguage}: codigo do idioma dos templates aprovados na Meta
 * (padrao pt_BR) — o worker envia mensagem TEMPLATE quando preenchido e
 * texto livre quando vazio (so funciona na janela de 24h do cliente).
 */
@ConfigurationProperties(prefix = "barberflow.whatsapp")
public record WhatsAppProperties(
    boolean enabled,
    String phoneNumberId,
    String accessToken,
    String appSecret,
    String verifyToken,
    String apiVersion,
    String templateLanguage) {

  public WhatsAppProperties {
    if (apiVersion == null || apiVersion.isBlank()) {
      apiVersion = "v23.0";
    }
    if (templateLanguage == null) {
      templateLanguage = "pt_BR";
    }
  }
}
