package com.barberflow.appointment.application;

import com.barberflow.appointment.infrastructure.AppointmentJpaEntity;
import com.barberflow.appointment.infrastructure.AppointmentRepository;
import com.barberflow.availability.api.Slot;
import com.barberflow.availability.application.AvailabilityQueryService;
import com.barberflow.availability.infrastructure.BusinessHoursRepository;
import com.barberflow.availability.infrastructure.ScheduleExceptionRepository;
import com.barberflow.tenant.infrastructure.TenantJpaEntity;
import com.barberflow.waitlist.infrastructure.SlotOfferJpaEntity;
import com.barberflow.waitlist.infrastructure.SlotOfferRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Revalidacao de slot no servidor (spec, secao 7.4): janela dentro do
 * expediente, sem atravessar meia-noite e livre de reservas ativas,
 * bloqueios de agenda e holds de lista de espera (spec, secao 9.2.4: vaga
 * ofertada nao e anunciada como livre). A restricao de exclusao da V4 e a
 * rede de seguranca final.
 */
@Component
public class SlotValidator {

  /** Estados ocupantes do intervalo (restricao de exclusao na V4). */
  static final List<String> ACTIVE_STATUSES = List.of("PENDING", "CONFIRMED");

  private final AppointmentRepository appointments;
  private final BusinessHoursRepository businessHours;
  private final ScheduleExceptionRepository scheduleExceptions;
  private final SlotOfferRepository slotOffers;
  private final AvailabilityQueryService availability;

  public SlotValidator(
      AppointmentRepository appointments,
      BusinessHoursRepository businessHours,
      ScheduleExceptionRepository scheduleExceptions,
      SlotOfferRepository slotOffers,
      AvailabilityQueryService availability) {
    this.appointments = appointments;
    this.businessHours = businessHours;
    this.scheduleExceptions = scheduleExceptions;
    this.slotOffers = slotOffers;
    this.availability = availability;
  }

  /**
   * Intervalo dentro do expediente do profissional e sem atravessar
   * meia-noite (400 em caso contrario).
   */
  public void validateWindow(TenantJpaEntity tenant, UUID barberId, Instant startsAt, Instant endsAt) {
    ZoneId zone = ZoneId.of(tenant.getTimezone());
    LocalTime startLocal = startsAt.atZone(zone).toLocalTime();
    LocalTime endLocal = endsAt.atZone(zone).toLocalTime();
    if (endLocal.isBefore(startLocal) || endLocal.equals(startLocal)) {
      throw badRequest("Reserva nao pode atravessar a meia-noite");
    }

    LocalDate date = startsAt.atZone(zone).toLocalDate();
    int weekday = date.getDayOfWeek().getValue() % 7;
    var windows = businessHours.findByTenantIdAndBarberIdAndWeekdayOrderByStartLocalAsc(
        tenant.getId(), barberId, weekday);
    boolean inside =
        windows.stream()
            .anyMatch(w -> w.getStartLocal().compareTo(startLocal) <= 0
                && endLocal.compareTo(w.getEndLocal()) <= 0);
    if (!inside) {
      throw badRequest("Horario fora do expediente do profissional");
    }
  }

  /**
   * {@link #validateWindow} + verificacao de conflitos (409 com horarios
   * alternativos, spec, secao 7.6): reservas ativas, excecoes de agenda e
   * holds de lista de espera. Para o aceite da oferta, o hold proprio e
   * ignorado ({@code ignoreOfferId}).
   */
  public void validateFree(
      TenantJpaEntity tenant,
      UUID barberId,
      UUID serviceId,
      Instant startsAt,
      Instant endsAt,
      UUID ignoreAppointmentId,
      UUID ignoreOfferId) {
    validateWindow(tenant, barberId, startsAt, endsAt);

    ZoneId zone = ZoneId.of(tenant.getTimezone());
    LocalDate date = startsAt.atZone(zone).toLocalDate();
    Instant dayStart = date.atStartOfDay(zone).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

    List<long[]> busy = new ArrayList<>();
    appointments
        .findByTenantIdAndBarberIdAndStatusInAndEndsAtAfterAndStartsAtBefore(
            tenant.getId(), barberId, ACTIVE_STATUSES, dayStart, dayEnd)
        .forEach(
            entity -> {
              if (!entity.getId().equals(ignoreAppointmentId)) {
                busy.add(
                    new long[] {
                      entity.getStartsAt().toEpochMilli(), entity.getEndsAt().toEpochMilli()
                    });
              }
            });
    scheduleExceptions
        .findByTenantIdAndBarberIdAndEndAtAfterAndStartAtBefore(
            tenant.getId(), barberId, dayStart, dayEnd)
        .forEach(
            exception ->
                busy.add(
                    new long[] {
                      exception.getStartAt().toEpochMilli(), exception.getEndAt().toEpochMilli()
                    }));
    // Holds de lista de espera: oferta PENDING e valida reserva o intervalo
    // (spec, secao 9.2, decisao de produto: vaga em hold nao e anunciada).
    Instant now = Instant.now();
    slotOffers
        .findActiveHolds(tenant.getId(), barberId, "PENDING")
        .stream()
        .filter(offer -> !offer.expirada(now))
        .filter(offer -> !offer.getId().equals(ignoreOfferId))
        .forEach(
            offer ->
                busy.add(
                    new long[] {offer.getStartsAt().toEpochMilli(), offer.getEndsAt().toEpochMilli()}));

    for (long[] interval : busy) {
      if (interval[0] < endsAt.toEpochMilli() && startsAt.toEpochMilli() < interval[1]) {
        throw new AppointmentConflictException(
            "Horario indisponivel para este profissional",
            alternatives(tenant.getId(), tenant.getTimezone(), barberId, serviceId, date));
      }
    }
  }

  /** Alternativas via motor de disponibilidade (ja exclui holds). */
  public List<Slot> alternatives(
      UUID tenantId, String timezone, UUID barberId, UUID serviceId, LocalDate date) {
    try {
      return availability.forTenant(tenantId, timezone, barberId, serviceId, date).slots();
    } catch (ResponseStatusException e) {
      return List.of();
    }
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
