package com.barberflow.notification.application;

/**
 * Tipos de evento da outbox (spec, secoes 9.1 e 9.2). Cada constante
 * corresponde a um template de WhatsApp na Fase 4; payloads sao JSON em
 * {@code payload_json} com ids e horarios, nunca dados sensentes extras.
 */
public final class OutboxEvents {

  /** Reserva criada (publico com Idempotency-Key ou aceite de oferta). */
  public static final String APPOINTMENT_CREATED = "APPOINTMENT_CREATED";

  /** Reserva cancelada pelo cliente ou pelo gestor (spec, secao 9.2.2). */
  public static final String APPOINTMENT_CANCELLED = "APPOINTMENT_CANCELLED";

  /** Reserva remarcada pelo cliente pelo link seguro. */
  public static final String APPOINTMENT_RESCHEDULED = "APPOINTMENT_RESCHEDULED";

  /** Vaga liberada ofertada a inscrito da lista de espera (spec, secao 9.2.4). */
  public static final String WAITLIST_SLOT_AVAILABLE = "WAITLIST_SLOT_AVAILABLE";

  /**
   * Lembrete 24h (Fase 4): agendado pelo ReminderWorker com chave de
   * idempotencia por reserva — reexecucoes do job nunca duplicam a mensagem.
   */
  public static final String APPOINTMENT_REMINDER = "APPOINTMENT_REMINDER";

  private OutboxEvents() {}
}
