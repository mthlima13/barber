package com.barberflow.availability.api;

import com.barberflow.availability.infrastructure.ScheduleExceptionJpaEntity;
import java.time.Instant;
import java.util.UUID;

/** Excecao de agenda persistida. */
public record ScheduleExceptionResponse(
    UUID id, Instant startAt, Instant endAt, String kind, Instant createdAt) {

  public static ScheduleExceptionResponse from(ScheduleExceptionJpaEntity entity) {
    return new ScheduleExceptionResponse(
        entity.getId(),
        entity.getStartAt(),
        entity.getEndAt(),
        entity.getKind(),
        entity.getCreatedAt());
  }
}
