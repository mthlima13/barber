package com.barberflow.tenant.api;

import com.barberflow.tenant.infrastructure.TenantJpaEntity;
import java.util.UUID;

/**
 * Dados publicos da barbearia (spec, linha 177): o que a pagina de
 * agendamento precisa para se apresentar. Sem dados internos (status
 * administrativo, timestamps).
 */
public record PublicBarbershopResponse(
    UUID id, String slug, String name, String timezone) {

  public static PublicBarbershopResponse from(TenantJpaEntity entity) {
    return new PublicBarbershopResponse(
        entity.getId(), entity.getSlug(), entity.getName(), entity.getTimezone());
  }
}
