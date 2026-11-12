package com.barberflow.audit.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventJpaEntity, UUID> {

  /** Listagem do dashboard (mais recentes primeiro). */
  List<AuditEventJpaEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
