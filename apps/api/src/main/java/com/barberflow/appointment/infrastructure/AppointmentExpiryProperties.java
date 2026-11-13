package com.barberflow.appointment.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Expiracao automatica de PENDING (spec, linha 137): job periodico que
 * cancela reservas pendentes alem do prazo (TTL) e libera o slot para a
 * lista de espera. DESLIGADO por padrao — habilitar em producao com um
 * prazo coerente (ex.: 24h); {@code pendingTtlHours=0} significa "expira
 * tudo que ja foi criado antes da varredura" (usado nos testes).
 */
@ConfigurationProperties(prefix = "barberflow.appointment-expiry")
public record AppointmentExpiryProperties(boolean enabled, long pendingTtlHours, String cron) {

  public AppointmentExpiryProperties {
    if (pendingTtlHours < 0) {
      pendingTtlHours = 24;
    }
    if (cron == null || cron.isBlank()) {
      cron = "0 */15 * * * *";
    }
  }
}
