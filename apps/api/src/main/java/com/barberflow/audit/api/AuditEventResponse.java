package com.barberflow.audit.api;

import com.barberflow.audit.infrastructure.AuditEventJpaEntity;
import java.time.Instant;
import java.util.UUID;

/** Representacao de evento de auditoria para o dashboard. */
public record AuditEventResponse(
    UUID id,
    UUID tenantId,
    UUID actorId,
    String action,
    String entityType,
    UUID entityId,
    Instant createdAt) {

  public static AuditEventResponse from(AuditEventJpaEntity entity) {
    return new AuditEventResponse(
        entity.getId(),
        entity.getTenantId(),
        entity.getActorId(),
        entity.getAction(),
        entity.getEntityType(),
        entity.getEntityId(),
        entity.getCreatedAt());
  }
}
