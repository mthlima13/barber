package com.barberflow.summary.api;

/**
 * Visao do dia no painel do gestor: reservas por status (com receita
 * esperada), fila de espera por status e saude da outbox do tenant.
 * Tudo agregado em uma chamada para os cartoes da tela de agenda.
 */
public record SummaryResponse(String date, AppointmentsSummary appointments, WaitlistSummary waitlist, OutboxSummary outbox) {

  /** Resumo das reservas da data (receita = pendentes+confirmadas+concluidas). */
  public record AppointmentsSummary(
      long total,
      long pending,
      long confirmed,
      long completed,
      long noShow,
      long cancelled,
      long expectedRevenueCents) {}

  /** Funil da lista de espera do tenant (acumulado, nao por data). */
  public record WaitlistSummary(long waiting, long offered, long fulfilled, long expired) {}

  /** Saude da entrega de notificacoes do tenant. */
  public record OutboxSummary(long pending, long failed) {}
}
