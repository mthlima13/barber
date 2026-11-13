package com.barberflow.appointment.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Representacao de reserva. {@code manageToken} so e exposto na criacao/
 * idempotencia (link seguro de gerenciamento do cliente — spec, secao 9.1).
 * {@code customerName} e resolvido no servidor (agenda do gestor e consulta
 * do cliente); pode vir nulo em respostas antigas em cache.
 */
public record AppointmentResponse(
    UUID id,
    UUID tenantId,
    UUID barberId,
    UUID customerId,
    String customerName,
    UUID serviceId,
    Instant startsAt,
    Instant endsAt,
    String status,
    long priceCents,
    int durationMinutes,
    String manageToken) {

  /** Versao sem o token (listagens e consultas). */
  public AppointmentResponse withoutToken() {
    return new AppointmentResponse(
        id, tenantId, barberId, customerId, customerName, serviceId, startsAt, endsAt, status,
        priceCents, durationMinutes, null);
  }
}
