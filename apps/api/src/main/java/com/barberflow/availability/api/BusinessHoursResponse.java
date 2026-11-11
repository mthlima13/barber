package com.barberflow.availability.api;

import com.barberflow.availability.infrastructure.BusinessHoursJpaEntity;
import java.time.LocalTime;
import java.util.UUID;

/** Janela de expediente persistida. */
public record BusinessHoursResponse(
    UUID id, int weekday, LocalTime startLocal, LocalTime endLocal) {

  public static BusinessHoursResponse from(BusinessHoursJpaEntity entity) {
    return new BusinessHoursResponse(
        entity.getId(), entity.getWeekday(), entity.getStartLocal(), entity.getEndLocal());
  }
}
