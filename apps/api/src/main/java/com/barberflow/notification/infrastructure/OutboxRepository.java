package com.barberflow.notification.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

  /** Observabilidade no dashboard (mais recentes primeiro). */
  List<OutboxEventJpaEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<OutboxEventJpaEntity> findByTenantIdAndIdempotencyKey(
      UUID tenantId, String idempotencyKey);

  /** Chave global (idempotencia de entrega entre tenants; usada em testes). */
  Optional<OutboxEventJpaEntity> findByIdempotencyKey(String idempotencyKey);

  /**
   * Worker (Fase 4): eventos PENDING vencidos (primeira tentativa tem
   * next_attempt_at nulo), em ordem de entrega; lote limitado por execucao.
   */
  @Query(
      """
      select e from OutboxEventJpaEntity e
      where e.status = :status
        and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)
      order by e.createdAt asc
      """)
  List<OutboxEventJpaEntity> findDue(
      @Param("status") String status, @Param("now") Instant now, Pageable pageable);

  /** Worker da Fase 4: eventos prontos, em ordem de entrega. */
  List<OutboxEventJpaEntity> findByStatusOrderByNextAttemptAtAsc(String status);

  /** Metrica: quantidade de eventos por status (scrape do Prometheus). */
  long countByStatus(String status);

  /** Metrica: created_at do evento PENDING mais antigo (idade da fila). */
  @Query(
      """
      select min(e.createdAt) from OutboxEventJpaEntity e
      where e.status = 'PENDING'
      """)
  Instant oldestPendingCreatedAt();

  /** Lembrete 24h: controle de idempotencia do job nos testes. */
  long countByEventType(String eventType);

  /** Resumo do dashboard: fila de entrega do tenant por status. */
  long countByTenantIdAndStatus(UUID tenantId, String status);
}
