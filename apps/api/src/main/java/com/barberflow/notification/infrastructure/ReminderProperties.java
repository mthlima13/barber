package com.barberflow.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lembrete 24h (Fase 4): job idempotente que agenda a mensagem na outbox
 * para reservas PENDING/CONFIRMED dentro da janela de antecedencia. A
 * entrega em si continua sendo do worker da outbox.
 */
@ConfigurationProperties(prefix = "barberflow.reminders")
public record ReminderProperties(boolean enabled, long windowHours, String cron) {

  public ReminderProperties {
    if (windowHours <= 0) {
      windowHours = 24;
    }
    if (cron == null || cron.isBlank()) {
      cron = "0 */15 * * * *";
    }
  }
}
