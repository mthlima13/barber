package com.barberflow.staff.api;

import com.barberflow.staff.infrastructure.BarberJpaEntity;
import java.time.Instant;
import java.util.UUID;

/** Representacao de profissional para a API. */
public record BarberResponse(
    UUID id,
    UUID userId,
    String displayName,
    boolean active,
    Instant createdAt) {

  /** Credenciais criadas para o profissional (papel BARBER). */
  public record Credentials(
      UUID barberId, UUID userId, String email, String role) {}

  public static BarberResponse from(BarberJpaEntity entity) {
    return new BarberResponse(
        entity.getId(),
        entity.getUserId(),
        entity.getDisplayName(),
        entity.isActive(),
        entity.getCreatedAt());
  }
}
