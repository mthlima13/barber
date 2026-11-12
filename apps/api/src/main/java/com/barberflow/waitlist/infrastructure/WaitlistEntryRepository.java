package com.barberflow.waitlist.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntryJpaEntity, UUID> {

  Optional<WaitlistEntryJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Fila do profissional em ordem de espera (spec, secao 9.2.3): quem
   * perdeu a oferta volta para o fim (updatedAt e tocado na expiracao). */
  List<WaitlistEntryJpaEntity> findByTenantIdAndStatusOrderByUpdatedAtAsc(
      UUID tenantId, String status);

  boolean existsByTenantIdAndCustomerIdAndBarberIdAndDesiredStartAtAndStatusIn(
      UUID tenantId, UUID customerId, UUID barberId, Instant desiredStartAt,
      Collection<String> statuses);

  /** Listagem do dashboard (mais recentes primeiro). */
  List<WaitlistEntryJpaEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  /** Canal de acesso LGPD: todas as inscricoes do titular. */
  List<WaitlistEntryJpaEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
