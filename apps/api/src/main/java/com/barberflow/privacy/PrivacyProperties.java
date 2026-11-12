package com.barberflow.privacy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LGPD (spec, linha 223): versao da politica de privacidade apresentada no
 * consentimento e politica de retencao (anonimizacao de clientes sem
 * atividade recente; regra final definida com orientacao juridica).
 */
@ConfigurationProperties(prefix = "barberflow.privacy")
public record PrivacyProperties(
    String policyVersion,
    boolean retentionEnabled,
    int retentionMonths,
    String retentionCron) {}
