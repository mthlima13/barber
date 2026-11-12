package com.barberflow.notification.api;

import com.barberflow.notification.infrastructure.OutboxEventJpaEntity;
import java.time.Instant;
import java.util.UUID;

/** Representacao de evento da outbox para o dashboard (observabilidade). */
public record OutboxEventResponse(
    UUID id,
    UUID tenantId,
    String eventType,
    String payloadJson,
    String status,
    int attemptCount,
    Instant nextAttemptAt,
    String idempotencyKey,
    String messageId,
    String lastError,
    Instant createdAt) {

  public static OutboxEventResponse from(OutboxEventJpaEntity entity) {
    return new OutboxEventResponse(
        entity.getId(),
        entity.getTenantId(),
        entity.getEventType(),
        entity.getPayloadJson(),
        entity.getStatus(),
        entity.getAttemptCount(),
        entity.getNextAttemptAt(),
        entity.getIdempotencyKey(),
        entity.getMessageId(),
        entity.getLastError(),
        entity.getCreatedAt());
  }
}
