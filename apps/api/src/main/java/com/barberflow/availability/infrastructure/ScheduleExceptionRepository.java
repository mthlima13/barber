package com.barberflow.availability.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleExceptionRepository extends JpaRepository<ScheduleExceptionJpaEntity, UUID> {

  List<ScheduleExceptionJpaEntity> findAllByTenantIdAndBarberIdOrderByStartAtAsc(
      UUID tenantId, UUID barberId);

  /** Bloqueios que intersectam {@code [dayStart, dayEnd)}. */
  List<ScheduleExceptionJpaEntity> findByTenantIdAndBarberIdAndEndAtAfterAndStartAtBefore(
      UUID tenantId, UUID barberId, Instant dayStart, Instant dayEnd);

  Optional<ScheduleExceptionJpaEntity> findByIdAndTenantIdAndBarberId(
      UUID id, UUID tenantId, UUID barberId);
}
