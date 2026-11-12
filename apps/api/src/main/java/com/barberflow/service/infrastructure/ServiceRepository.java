package com.barberflow.service.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<ServiceJpaEntity, UUID> {

  List<ServiceJpaEntity> findAllByTenantIdOrderByCreatedAtAsc(UUID tenantId);

  /** Catalogo publico: apenas servicos ativos. */
  List<ServiceJpaEntity> findAllByTenantIdAndActiveTrueOrderByCreatedAtAsc(UUID tenantId);

  Optional<ServiceJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
