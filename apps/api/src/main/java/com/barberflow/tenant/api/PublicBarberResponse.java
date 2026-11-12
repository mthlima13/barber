package com.barberflow.tenant.api;

import com.barberflow.staff.infrastructure.BarberJpaEntity;
import java.util.UUID;

/**
 * Profissional visivel ao cliente (spec, linha 179): apenas ativos, sem
 * campos internos (userId, timestamps).
 */
public record PublicBarberResponse(UUID id, String displayName) {

  public static PublicBarberResponse from(BarberJpaEntity entity) {
    return new PublicBarberResponse(entity.getId(), entity.getDisplayName());
  }
}
