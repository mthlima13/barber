package com.barberflow.staff.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BarberRepository extends JpaRepository<BarberJpaEntity, UUID> {

  List<BarberJpaEntity> findAllByTenantIdOrderByCreatedAtAsc(UUID tenantId);

  /** Catalogo publico: apenas profissionais ativos. */
  List<BarberJpaEntity> findAllByTenantIdAndActiveTrueOrderByCreatedAtAsc(UUID tenantId);

  Optional<BarberJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Perfil do profissional vinculado a conta (papel BARBER, spec linha 218). */
  Optional<BarberJpaEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
