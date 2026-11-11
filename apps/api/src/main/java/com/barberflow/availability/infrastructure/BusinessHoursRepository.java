package com.barberflow.availability.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessHoursRepository extends JpaRepository<BusinessHoursJpaEntity, UUID> {

  List<BusinessHoursJpaEntity> findAllByTenantIdAndBarberIdOrderByWeekdayAscStartLocalAsc(
      UUID tenantId, UUID barberId);

  List<BusinessHoursJpaEntity> findByTenantIdAndBarberIdAndWeekdayOrderByStartLocalAsc(
      UUID tenantId, UUID barberId, int weekday);

  void deleteAllByTenantIdAndBarberId(UUID tenantId, UUID barberId);
}
