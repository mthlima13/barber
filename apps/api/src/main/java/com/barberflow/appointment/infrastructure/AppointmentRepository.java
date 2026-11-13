package com.barberflow.appointment.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<AppointmentJpaEntity, UUID> {

  Optional<AppointmentJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<AppointmentJpaEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String key);

  /** Reservas ativas do profissional que intersectam o dia. */
  List<AppointmentJpaEntity> findByTenantIdAndBarberIdAndStatusInAndEndsAtAfterAndStartsAtBefore(
      UUID tenantId,
      UUID barberId,
      Collection<String> statuses,
      Instant dayStart,
      Instant dayEnd);

  List<AppointmentJpaEntity> findByTenantIdAndBarberIdAndStartsAtBetweenOrderByStartsAtAsc(
      UUID tenantId, UUID barberId, Instant dayStart, Instant dayEnd);

  List<AppointmentJpaEntity> findByTenantIdAndStatusInAndStartsAtBetweenOrderByStartsAtAsc(
      UUID tenantId, Collection<String> statuses, Instant dayStart, Instant dayEnd);

  /** Canal de acesso LGPD: todas as reservas do titular (mais recentes). */
  List<AppointmentJpaEntity> findByCustomerIdOrderByStartsAtDesc(UUID customerId);

  /** Lembrete 24h (Fase 4): reservas na janela de antecedencia, todos os tenants. */
  List<AppointmentJpaEntity> findByStatusInAndStartsAtBetween(
      Collection<String> statuses, Instant windowStart, Instant windowEnd);
}
