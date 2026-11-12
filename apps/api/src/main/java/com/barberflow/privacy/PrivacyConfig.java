package com.barberflow.privacy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Configuracao de privacidade/LGPD: properties + agendamento da retencao. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(PrivacyProperties.class)
public class PrivacyConfig {}
