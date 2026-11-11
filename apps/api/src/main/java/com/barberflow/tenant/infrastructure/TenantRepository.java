package com.barberflow.tenant.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantJpaEntity, UUID> {

  Optional<TenantJpaEntity> findBySlug(String slug);
}
