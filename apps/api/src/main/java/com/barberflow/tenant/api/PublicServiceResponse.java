package com.barberflow.tenant.api;

import com.barberflow.service.infrastructure.ServiceJpaEntity;
import java.util.UUID;

/**
 * Servico visivel ao cliente (spec, linha 178): apenas ativos, com preco
 * (informacao necessaria para a escolha na pagina de agendamento), sem
 * campos administrativos (timestamps).
 */
public record PublicServiceResponse(
    UUID id,
    String name,
    String description,
    int durationMinutes,
    long priceCents) {

  public static PublicServiceResponse from(ServiceJpaEntity entity) {
    return new PublicServiceResponse(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.getDurationMinutes(),
        entity.getPriceCents());
  }
}
