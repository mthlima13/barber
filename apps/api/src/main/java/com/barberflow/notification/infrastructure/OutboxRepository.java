package com.barberflow.notification.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

  /** Observabilidade no dashboard (mais recentes primeiro). */
  List<OutboxEventJpaEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<OutboxEventJpaEntity> findByTenantIdAndIdempotencyKey(
      UUID tenantId, String idempotencyKey);

  /** Worker da Fase 4: eventos prontos, em ordem de entrega. */
  List<OutboxEventJpaEntity> findByStatusOrderByNextAttemptAtAsc(String status);
}
