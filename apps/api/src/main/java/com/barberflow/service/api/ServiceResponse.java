package com.barberflow.service.api;

import com.barberflow.service.infrastructure.ServiceJpaEntity;
import java.time.Instant;
import java.util.UUID;

/** Representacao de servico para a API. */
public record ServiceResponse(
    UUID id,
    String name,
    String description,
    int durationMinutes,
    long priceCents,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static ServiceResponse from(ServiceJpaEntity entity) {
    return new ServiceResponse(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.getDurationMinutes(),
        entity.getPriceCents(),
        entity.isActive(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
