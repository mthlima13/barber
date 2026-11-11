package com.barberflow.appointment.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<CustomerJpaEntity, UUID> {

  Optional<CustomerJpaEntity> findByTenantIdAndPhoneE164(UUID tenantId, String phoneE164);
}
