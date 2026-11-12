package com.barberflow.notification.application;

import com.barberflow.notification.infrastructure.WhatsAppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * Integracao WhatsApp (spec, secao 12): properties, client (HTTP real quando
 * habilitado; log quando desabilitado) e agendamento do worker.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppConfig {

  @Bean
  WhatsAppClient whatsAppClient(WhatsAppProperties properties) {
    return properties.enabled()
        ? new HttpWhatsAppClient(properties)
        : new LoggingWhatsAppClient(properties);
  }
}
