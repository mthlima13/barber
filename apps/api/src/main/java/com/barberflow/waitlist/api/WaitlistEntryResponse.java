package com.barberflow.waitlist.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Representacao de inscricao na lista de espera. Quando OFFERED, inclui a
 * oferta corrente com o {@code acceptToken} em claro apenas para o dono da
 * inscricao (link seguro) e para o gestor (reenvio manual no MVP); no banco
 * so existe o hash (spec, secao 6).
 */
public record WaitlistEntryResponse(
    UUID id,
    UUID tenantId,
    UUID barberId,
    UUID serviceId,
    Instant startsAt,
    Instant endsAt,
    String status,
    Instant expiresAt,
    OfferView offer,
    String manageToken) {

  /** Oferta corrente da inscricao (nula enquanto WAITING/CANCELLED). */
  public record OfferView(
      UUID offerId, Instant startsAt, Instant endsAt, Instant expiresAt, String acceptToken) {}
}
