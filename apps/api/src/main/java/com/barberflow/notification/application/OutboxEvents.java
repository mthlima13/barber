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

  /**
   * Expiracao automatica de PENDING (job): a reserva estava pendente de
   * confirmacao alem do prazo configurado e foi cancelada pelo sistema,
   * liberando o slot (spec, linha 137).
   */
  public static final String APPOINTMENT_EXPIRED = "APPOINTMENT_EXPIRED";

  /**
   * Notificacoes ao GESTOR (audience=manager no payload; destino e o
   * manager_phone do tenant): reserva nova e inscricao na fila de espera.
   */
  public static final String MANAGER_APPOINTMENT_CREATED = "MANAGER_APPOINTMENT_CREATED";

  public static final String MANAGER_WAITLIST_JOINED = "MANAGER_WAITLIST_JOINED";

  /** Cancelamento e remarcacao feitos pelo CLIENTE (link seguro). */
  public static final String MANAGER_APPOINTMENT_CANCELLED = "MANAGER_APPOINTMENT_CANCELLED";

  public static final String MANAGER_APPOINTMENT_RESCHEDULED = "MANAGER_APPOINTMENT_RESCHEDULED";

  private OutboxEvents() {}
}
