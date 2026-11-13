package com.barberflow.waitlist.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Inscricao na fila de espera para o dashboard (gestor): cliente resolvido
 * em nome/telefone e oferta corrente com o slot ofertado — a pagina nao
 * precisa resolver ids nos catalogos para mostrar quem esta na fila.
 * Sem tokens de cliente (o aceite continua indo apenas pelo link seguro).
 */
public record WaitlistDashboardResponse(
    UUID id,
    UUID barberId,
    UUID serviceId,
    String customerName,
    String customerPhone,
    Instant desiredStartAt,
    Instant desiredEndAt,
    String status,
    Instant createdAt,
    Instant expiresAt,
    OfferView offer) {

  /** Oferta corrente da inscricao (nula enquanto WAITING/CANCELLED). */
  public record OfferView(UUID offerId, Instant startsAt, Instant endsAt, Instant expiresAt) {}
}
