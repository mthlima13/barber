package com.barberflow.appointment.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<CustomerJpaEntity, UUID> {

  Optional<CustomerJpaEntity> findByTenantIdAndPhoneE164(UUID tenantId, String phoneE164);

  /**
   * Retencao LGPD (spec, linha 223): clientes nao anonimizados sem nenhuma
   * reserva posterior ao corte (nenhuma atividade recente identificavel).
   */
  @Query(
      """
      select c from CustomerJpaEntity c
      where c.anonymizedAt is null
        and not exists (
          select a from AppointmentJpaEntity a
          where a.customerId = c.id and a.startsAt > :cutoff)
      """)
  List<CustomerJpaEntity> findStale(@Param("cutoff") Instant cutoff);
}
