package com.barberflow.availability.application;

import com.barberflow.availability.api.AvailabilityResponse;
import com.barberflow.availability.api.Slot;
import com.barberflow.availability.infrastructure.AvailabilityJdbcRepository;
import com.barberflow.availability.infrastructure.BusinessHoursRepository;
import com.barberflow.availability.infrastructure.ScheduleExceptionRepository;
import com.barberflow.service.infrastructure.ServiceJpaEntity;
import com.barberflow.service.infrastructure.ServiceRepository;
import com.barberflow.staff.infrastructure.BarberJpaEntity;
import com.barberflow.staff.infrastructure.BarberRepository;
import com.barberflow.tenant.infrastructure.TenantJpaEntity;
import com.barberflow.tenant.infrastructure.TenantRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Motor de disponibilidade (spec, secao 7): calcula intervalos livres com base
 * em expediente, excecoes, duracao do servico e reservas existentes.
 *
 * <p>Horarios calculados no fuso IANA da barbearia e expostos em UTC.
 * Intervalos sao semiabertos {@code [start, end)} — atendimentos consecutivos
 * podem compartilhar a fronteira. A disponibilidade e estimativa; a
 * confirmacao do agendamento revalida no servidor (Fase 2).</p>
 */
@Service
public class AvailabilityQueryService {

  private final TenantRepository tenants;
  private final BarberRepository barbers;
  private final ServiceRepository services;
  private final BusinessHoursRepository businessHours;
  private final ScheduleExceptionRepository exceptions;
  private final AvailabilityJdbcRepository availabilityJdbc;
  private final int slotStepMinutes;
  private final int minLeadMinutes;

  public AvailabilityQueryService(
      TenantRepository tenants,
      BarberRepository barbers,
      ServiceRepository services,
      BusinessHoursRepository businessHours,
      ScheduleExceptionRepository exceptions,
      AvailabilityJdbcRepository availabilityJdbc,
      @Value("${barberflow.availability.slot-step-minutes:15}") int slotStepMinutes,
      @Value("${barberflow.availability.min-lead-minutes:0}") int minLeadMinutes) {
    this.tenants = tenants;
    this.barbers = barbers;
    this.services = services;
    this.businessHours = businessHours;
    this.exceptions = exceptions;
    this.availabilityJdbc = availabilityJdbc;
    this.slotStepMinutes = slotStepMinutes;
    this.minLeadMinutes = minLeadMinutes;
  }

  /** Endpoint publico (spec, secao 10): barbearia identificada pelo slug. */
  @Transactional(readOnly = true)
  public AvailabilityResponse bySlug(
      String slug, UUID barberId, UUID serviceId, LocalDate date) {
    TenantJpaEntity tenant =
        tenants
            .findBySlug(slug)
            .orElseThrow(
                () -> notFound("Barbearia nao encontrada: '" + slug + "'"));
    return compute(tenant.getId(), tenant.getTimezone(), barberId, serviceId, date);
  }

  @Transactional(readOnly = true)
  public AvailabilityResponse forTenant(
      UUID tenantId, String timezone, UUID barberId, UUID serviceId, LocalDate date) {
    return compute(tenantId, timezone, barberId, serviceId, date);
  }

  private AvailabilityResponse compute(
      UUID tenantId, String timezone, UUID barberId, UUID serviceId, LocalDate date) {
    BarberJpaEntity barber =
        barbers
            .findByIdAndTenantId(barberId, tenantId)
            .orElseThrow(() -> notFound("Profissional nao encontrado para esta barbearia"));
    if (!barber.isActive()) {
      throw notFound("Profissional nao encontrado para esta barbearia");
    }

    ServiceJpaEntity service =
        services
            .findByIdAndTenantId(serviceId, tenantId)
            .orElseThrow(() -> notFound("Servico nao encontrado para esta barbearia"));
    if (!service.isActive()) {
      throw notFound("Servico nao encontrado para esta barbearia");
    }

    if (!availabilityJdbc.serviceEnabled(tenantId, barberId, serviceId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Servico nao habilitado para este profissional");
    }

    ZoneId zone = ZoneId.of(timezone);
    // 0=domingo ... 6=sabado (DayOfWeek: segunda=1 ... domingo=7)
    int weekday = date.getDayOfWeek().getValue() % 7;

    var windows =
        businessHours.findByTenantIdAndBarberIdAndWeekdayOrderByStartLocalAsc(
            tenantId, barberId, weekday);
    if (windows.isEmpty()) {
      return new AvailabilityResponse(
          date, timezone, barberId, serviceId, service.getDurationMinutes(), List.of());
    }

    Instant dayStart = date.atStartOfDay(zone).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
    Instant cutoff = Instant.now().plus(Duration.ofMinutes(minLeadMinutes));

    List<long[]> busy = new ArrayList<>();
    for (var exception :
        exceptions.findByTenantIdAndBarberIdAndEndAtAfterAndStartAtBefore(
            tenantId, barberId, dayStart, dayEnd)) {
      busy.add(new long[] {exception.getStartAt().toEpochMilli(), exception.getEndAt().toEpochMilli()});
    }
    busy.addAll(availabilityJdbc.reservedIntervals(tenantId, barberId, dayStart, dayEnd));

    Duration duration = Duration.ofMinutes(service.getDurationMinutes());
    Duration step = Duration.ofMinutes(slotStepMinutes);
    List<Slot> slots = new ArrayList<>();

    for (var window : windows) {
      LocalTime cursor = window.getStartLocal();
      while (!date.atTime(cursor).atZone(zone).plus(duration).toInstant()
          .isAfter(date.atTime(window.getEndLocal()).atZone(zone).toInstant())) {
        Instant start = date.atTime(cursor).atZone(zone).toInstant();
        Instant end = start.plus(duration);

        boolean free = !start.isBefore(cutoff);
        if (free) {
          for (long[] interval : busy) {
            if (interval[0] < end.toEpochMilli() && start.toEpochMilli() < interval[1]) {
              free = false;
              break;
            }
          }
        }
        if (free) {
          slots.add(new Slot(start, end));
        }
        cursor = cursor.plusMinutes(slotStepMinutes);
      }
    }

    return new AvailabilityResponse(
        date, timezone, barberId, serviceId, service.getDurationMinutes(), slots);
  }

  private ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }
}
