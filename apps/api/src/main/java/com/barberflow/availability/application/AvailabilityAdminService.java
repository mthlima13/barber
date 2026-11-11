package com.barberflow.availability.application;

import com.barberflow.availability.api.BusinessHoursRequest;
import com.barberflow.availability.api.BusinessHoursResponse;
import com.barberflow.availability.api.ScheduleExceptionRequest;
import com.barberflow.availability.api.ScheduleExceptionResponse;
import com.barberflow.availability.infrastructure.BusinessHoursJpaEntity;
import com.barberflow.availability.infrastructure.BusinessHoursRepository;
import com.barberflow.availability.infrastructure.ScheduleExceptionJpaEntity;
import com.barberflow.availability.infrastructure.ScheduleExceptionRepository;
import com.barberflow.staff.infrastructure.BarberJpaEntity;
import com.barberflow.staff.infrastructure.BarberRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gestao de expediente e excecoes pelo painel do gestor (spec, secao 10).
 */
@Service
public class AvailabilityAdminService {

  private static final Set<String> KINDS = Set.of("DAY_OFF", "BLOCK");

  private final BusinessHoursRepository businessHours;
  private final ScheduleExceptionRepository exceptions;
  private final BarberRepository barbers;

  public AvailabilityAdminService(
      BusinessHoursRepository businessHours,
      ScheduleExceptionRepository exceptions,
      BarberRepository barbers) {
    this.businessHours = businessHours;
    this.exceptions = exceptions;
    this.barbers = barbers;
  }

  @Transactional(readOnly = true)
  public List<BusinessHoursResponse> listHours(UUID tenantId, UUID barberId) {
    findTenantBarber(tenantId, barberId);
    return businessHours
        .findAllByTenantIdAndBarberIdOrderByWeekdayAscStartLocalAsc(tenantId, barberId)
        .stream()
        .map(BusinessHoursResponse::from)
        .toList();
  }

  /** Substitui o expediente semanal do profissional (delete + insert atomicos). */
  @Transactional
  public List<BusinessHoursResponse> replaceHours(
      UUID tenantId, UUID barberId, List<BusinessHoursRequest> requests) {
    findTenantBarber(tenantId, barberId);

    Set<Integer> weekdays = new HashSet<>();
    for (var request : requests) {
      if (request.startLocal() == null
          || request.endLocal() == null
          || !request.startLocal().isBefore(request.endLocal())) {
        throw badRequest("Expediente precisa ter inicio antes do fim (weekday "
            + request.weekday() + ")");
      }
      if (!weekdays.add(request.weekday())) {
        throw badRequest("Duplicata de weekday no expediente: " + request.weekday());
      }
    }

    businessHours.deleteAllByTenantIdAndBarberId(tenantId, barberId);
    List<BusinessHoursJpaEntity> saved =
        businessHours.saveAll(
            requests.stream()
                .map(
                    request ->
                        BusinessHoursJpaEntity.novo(
                            tenantId,
                            barberId,
                            request.weekday(),
                            request.startLocal(),
                            request.endLocal()))
                .toList());
    return saved.stream().map(BusinessHoursResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<ScheduleExceptionResponse> listExceptions(UUID tenantId, UUID barberId) {
    findTenantBarber(tenantId, barberId);
    return exceptions.findAllByTenantIdAndBarberIdOrderByStartAtAsc(tenantId, barberId).stream()
        .map(ScheduleExceptionResponse::from)
        .toList();
  }

  @Transactional
  public ScheduleExceptionResponse createException(
      UUID tenantId, UUID barberId, ScheduleExceptionRequest request) {
    findTenantBarber(tenantId, barberId);

    if (request.startAt() == null
        || request.endAt() == null
        || !request.startAt().isBefore(request.endAt())) {
      throw badRequest("Bloqueio precisa ter inicio antes do fim");
    }
    if (request.kind() == null || !KINDS.contains(request.kind())) {
      throw badRequest("kind deve ser DAY_OFF ou BLOCK");
    }

    ScheduleExceptionJpaEntity saved =
        exceptions.save(
            ScheduleExceptionJpaEntity.novo(
                tenantId, barberId, request.startAt(), request.endAt(), request.kind()));
    return ScheduleExceptionResponse.from(saved);
  }

  @Transactional
  public void deleteException(UUID tenantId, UUID barberId, UUID exceptionId) {
    findTenantBarber(tenantId, barberId);
    ScheduleExceptionJpaEntity entity =
        exceptions
            .findByIdAndTenantIdAndBarberId(exceptionId, tenantId, barberId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Bloqueio nao encontrado para este profissional"));
    exceptions.delete(entity);
  }

  private BarberJpaEntity findTenantBarber(UUID tenantId, UUID barberId) {
    BarberJpaEntity barber =
        barbers
            .findByIdAndTenantId(barberId, tenantId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profissional nao encontrado para esta barbearia"));
    return barber;
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
