package com.barberflow.waitlist.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotOfferRepository extends JpaRepository<SlotOfferJpaEntity, UUID> {

  Optional<SlotOfferJpaEntity> findByTokenHash(String tokenHash);

  List<SlotOfferJpaEntity> findByTenantIdAndStatus(UUID tenantId, String status);

  /** Ofertas PENDING da entrada (no maximo uma valida por vez). */
  List<SlotOfferJpaEntity> findByTenantIdAndWaitlistEntryIdAndStatus(
      UUID tenantId, UUID waitlistEntryId, String status);

  /**
   * Holds ativos do tenant: ofertas PENDING de entradas do profissional,
   * usados como intervalos ocupados na validacao de slots.
   */
  @org.springframework.data.jpa.repository.Query(
      "SELECT o FROM SlotOfferJpaEntity o"
          + " JOIN WaitlistEntryJpaEntity e ON o.waitlistEntryId = e.id"
          + " WHERE o.tenantId = :tenantId AND e.barberId = :barberId AND o.status = :status")
  List<SlotOfferJpaEntity> findActiveHolds(UUID tenantId, UUID barberId, String status);
}
