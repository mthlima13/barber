package com.barberflow.appointment.application;

import com.barberflow.appointment.api.AppointmentResponse;
import com.barberflow.appointment.api.CreateAppointmentRequest;
import com.barberflow.appointment.infrastructure.AppointmentJpaEntity;
import com.barberflow.appointment.infrastructure.AppointmentRepository;
import com.barberflow.appointment.infrastructure.CustomerJpaEntity;
import com.barberflow.appointment.infrastructure.CustomerRepository;
import com.barberflow.availability.api.Slot;
import com.barberflow.availability.application.AvailabilityQueryService;
import com.barberflow.auth.domain.AuthPrincipal;
import com.barberflow.auth.infrastructure.JwtService;
import com.barberflow.service.infrastructure.ServiceJpaEntity;
import com.barberflow.service.infrastructure.ServiceRepository;
import com.barberflow.staff.infrastructure.BarberJpaEntity;
import com.barberflow.staff.infrastructure.BarberRepository;
import com.barberflow.tenant.infrastructure.TenantJpaEntity;
import com.barberflow.tenant.infrastructure.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reservas (spec, secoes 7, 8 e 9.1): criacao transacional com revalidacao de
 * disponibilidade no servidor, cancelamento e reagendamento atomico pelo link
 * seguro do cliente, e agenda do dashboard com transicoes de status.
 */
@Service
public class AppointmentAppService {

  /** Estados ocupantes do intervalo (restricao de exclusao na V4). */
  static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "CONFIRMED");

  /** Transicoes validas da maquina de estados (spec, secao 8). */
  static final Set<String> VALID_TRANSITIONS =
      Set.of(
          "PENDING->CONFIRMED",
          "PENDING->CANCELLED",
          "CONFIRMED->COMPLETED",
          "CONFIRMED->CANCELLED",
          "CONFIRMED->NO_SHOW");

  private static final List<String> DAY_STATUSES = List.of("PENDING", "CONFIRMED", "COMPLETED",
      "CANCELLED", "NO_SHOW");

  private final TenantRepository tenants;
  private final BarberRepository barbers;
  private final ServiceRepository services;
  private final CustomerRepository customers;
  private final AppointmentRepository appointments;
  private final com.barberflow.availability.infrastructure.BusinessHoursRepository
      businessHoursRepo;
  private final com.barberflow.availability.infrastructure.ScheduleExceptionRepository
      scheduleExceptions;
  private final com.barberflow.availability.infrastructure.AvailabilityJdbcRepository
      availabilityJdbc;
  private final AvailabilityQueryService availability;
  private final JwtService jwt;

  public AppointmentAppService(
      TenantRepository tenants,
      BarberRepository barbers,
      ServiceRepository services,
      CustomerRepository customers,
      AppointmentRepository appointments,
      com.barberflow.availability.infrastructure.BusinessHoursRepository businessHoursRepo,
      com.barberflow.availability.infrastructure.ScheduleExceptionRepository scheduleExceptions,
      com.barberflow.availability.infrastructure.AvailabilityJdbcRepository availabilityJdbc,
      AvailabilityQueryService availability,
      JwtService jwt) {
    this.tenants = tenants;
    this.barbers = barbers;
    this.services = services;
    this.customers = customers;
    this.appointments = appointments;
    this.businessHoursRepo = businessHoursRepo;
    this.scheduleExceptions = scheduleExceptions;
    this.availabilityJdbc = availabilityJdbc;
    this.availability = availability;
    this.jwt = jwt;
  }

  /** Criacao de reserva com Idempotency-Key opcional (spec, secao 9.1). */
  @Transactional
  public CreateResult create(CreateAppointmentRequest request, String idempotencyKey) {
    TenantJpaEntity tenant =
        tenants
            .findBySlug(request.slug())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Barbearia nao encontrada: '" + request.slug() + "'"));

    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      Optional<AppointmentJpaEntity> existing =
          appointments.findByTenantIdAndIdempotencyKey(tenant.getId(), idempotencyKey);
      if (existing.isPresent()) {
        return new CreateResult(
            toResponse(existing.get(), manageToken(existing.get())), false);
      }
    }

    if (request.startsAt().isBefore(java.time.Instant.now())) {
      throw badRequest("Horario da reserva ja passou");
    }

    BarberJpaEntity barber = requireActiveBarber(tenant.getId(), request.barberId());
    ServiceJpaEntity service = requireActiveService(tenant.getId(), request.serviceId());
    if (!service.isActive() || !barber.isActive()) {
      throw notFound("Profissional ou servico indisponivel nesta barbearia");
    }
    if (!availabilityJdbc.serviceEnabled(tenant.getId(), barber.getId(), service.getId())) {
      throw badRequest("Servico nao habilitado para este profissional");
    }

    // Revalidacao no servidor (spec, secao 7.4): duracao sempre do servico.
    Instant endsAt = request.startsAt().plus(Duration.ofMinutes(service.getDurationMinutes()));
    validateSlot(tenant, barber, service, request.startsAt(), endsAt, null);

    CustomerJpaEntity customer =
        getOrCreateCustomer(tenant.getId(), request.customer().name(), request.customer().phone());

    AppointmentJpaEntity saved;
    try {
      saved =
          appointments.save(
              AppointmentJpaEntity.novo(
                  tenant.getId(),
                  barber.getId(),
                  customer.getId(),
                  service.getId(),
                  request.startsAt(),
                  endsAt,
                  service.getPriceCents(),
                  service.getDurationMinutes(),
                  idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey));
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      // Corrida perdida para a restricao de exclusao: 409 com alternativas.
      ZoneId zone = ZoneId.of(tenant.getTimezone());
      throw new AppointmentConflictException(
          "Horario indisponivel para este profissional",
          alternatives(
              tenant.getId(),
              tenant.getTimezone(),
              barber.getId(),
              service.getId(),
              request.startsAt().atZone(zone).toLocalDate()));
    }

    return new CreateResult(toResponse(saved, manageToken(saved)), true);
  }

  /** Resultado da criacao: distingue reserva nova de replay idempotente. */
  public record CreateResult(AppointmentResponse appointment, boolean created) {}

  /** Reserva do proprio link seguro (token de gerenciamento). */
  @Transactional(readOnly = true)
  public AppointmentResponse getForCustomer(AuthPrincipal principal, UUID appointmentId) {
    return toResponse(loadForCustomer(principal, appointmentId), null);
  }

  /** Cancelamento pelo link seguro: PENDING/CONFIRMED -> CANCELLED. */
  @Transactional
  public AppointmentResponse cancelForCustomer(AuthPrincipal principal, UUID appointmentId) {
    AppointmentJpaEntity entity = loadForCustomer(principal, appointmentId);
    transition(entity, "CANCELLED");
    // TODO(fase-4): evento AppointmentCancelled na outbox, na mesma transacao.
    return toResponse(entity, null);
  }

  /** Reagendamento atomico: valida o novo intervalo e atualiza a mesma linha. */
  @Transactional
  public AppointmentResponse rescheduleForCustomer(
      AuthPrincipal principal, UUID appointmentId, java.time.Instant newStartsAt) {
    if (newStartsAt.isBefore(java.time.Instant.now())) {
      throw badRequest("Novo horario da reserva ja passou");
    }
    AppointmentJpaEntity entity = loadForCustomer(principal, appointmentId);
    TenantJpaEntity tenant = requireTenant(entity.getTenantId());
    ServiceJpaEntity service =
        services
            .findByIdAndTenantId(entity.getServiceId(), entity.getTenantId())
            .orElseThrow(() -> notFound("Servico da reserva nao existe mais"));

    Instant newEndsAt = newStartsAt.plus(Duration.ofMinutes(entity.getDurationMinutesSnapshot()));
    validateSlot(tenant, null, service, newStartsAt, newEndsAt, entity.getId());
    entity.remarcar(newStartsAt, newEndsAt);
    // TODO(fase-4): evento de reagendamento na outbox.
    return toResponse(entity, null);
  }

  /** Agenda do dashboard por dia (fuso da barbearia), com filtro opcional. */
  @Transactional(readOnly = true)
  public List<AppointmentResponse> agenda(
      UUID tenantId, LocalDate date, UUID barberId) {
    TenantJpaEntity tenant = requireTenant(tenantId);
    ZoneId zone = ZoneId.of(tenant.getTimezone());
    Instant dayStart = date.atStartOfDay(zone).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

    List<AppointmentJpaEntity> rows =
        barberId == null
            ? appointments
                .findByTenantIdAndStatusInAndStartsAtBetweenOrderByStartsAtAsc(
                    tenantId, DAY_STATUSES, dayStart, dayEnd)
            : appointments.findByTenantIdAndBarberIdAndStartsAtBetweenOrderByStartsAtAsc(
                tenantId, barberId, dayStart, dayEnd);

    return rows.stream().map(entity -> toResponse(entity, null)).toList();
  }

  /** Atualizacao de status pelo gestor, respeitando a maquina de estados. */
  @Transactional
  public AppointmentResponse updateStatus(UUID tenantId, UUID appointmentId, String newStatus) {
    AppointmentJpaEntity entity =
        appointments
            .findByIdAndTenantId(appointmentId, tenantId)
            .orElseThrow(() -> notFound("Reserva nao encontrada para esta barbearia"));
    transition(entity, newStatus);
    return toResponse(entity, null);
  }

  // ------------------------------------------------------------------ internals

  private void validateSlot(
      TenantJpaEntity tenant,
      BarberJpaEntity barber,
      ServiceJpaEntity service,
      Instant startsAt,
      Instant endsAt,
      UUID ignoreAppointmentId) {
    UUID barberId = barber != null ? barber.getId() : null;
    // Para reagendamento, o barbeiro vem da reserva existente.
    if (barberId == null) {
      barberId =
          appointments
              .findById(ignoreAppointmentId)
              .map(AppointmentJpaEntity::getBarberId)
              .orElseThrow(() -> notFound("Reserva nao encontrada"));
    }

    ZoneId zone = ZoneId.of(tenant.getTimezone());
    LocalDate date = startsAt.atZone(zone).toLocalDate();
    LocalTime startLocal = startsAt.atZone(zone).toLocalTime();
    LocalTime endLocal = endsAt.atZone(zone).toLocalTime();
    if (endLocal.isBefore(startLocal) || endLocal.equals(startLocal)) {
      throw badRequest("Reserva nao pode atravessar a meia-noite");
    }
    int weekday = date.getDayOfWeek().getValue() % 7;

    // Janela de expediente precisa conter o intervalo (spec, secao 7.2).
    var windows = businessHoursRepo.findByTenantIdAndBarberIdAndWeekdayOrderByStartLocalAsc(
        tenant.getId(), barberId, weekday);
    boolean inside =
        windows.stream()
            .anyMatch(w -> w.getStartLocal().compareTo(startLocal) <= 0
                && endLocal.compareTo(w.getEndLocal()) <= 0);
    if (!inside) {
      throw badRequest("Horario fora do expediente do profissional");
    }

    // Sobreposicao com reservas ativas ou bloqueios -> 409 com alternativas
    // (spec, secao 7.6). A restricao de exclusao da V4 e a rede de seguranca.
    List<long[]> busy = new ArrayList<>();
    appointments
        .findByTenantIdAndBarberIdAndStatusInAndEndsAtAfterAndStartsAtBefore(
            tenant.getId(), barberId, ACTIVE_STATUSES, date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant())
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
            tenant.getId(), barberId, date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant())
        .forEach(
            exception ->
                busy.add(
                    new long[] {
                      exception.getStartAt().toEpochMilli(), exception.getEndAt().toEpochMilli()
                    }));

    for (long[] interval : busy) {
      if (interval[0] < endsAt.toEpochMilli() && startsAt.toEpochMilli() < interval[1]) {
        throw new AppointmentConflictException(
            "Horario indisponivel para este profissional",
            alternatives(tenant.getId(), tenant.getTimezone(), barberId, service.getId(), date));
      }
    }
  }

  // Bloqueios de agenda (schedule_exceptions) via AvailabilityQueryService:
  // reuso do calculo para listar alternativas no 409.
  private List<Slot> alternatives(
      UUID tenantId, String timezone, UUID barberId, UUID serviceId, LocalDate date) {
    try {
      return availability.forTenant(tenantId, timezone, barberId, serviceId, date).slots();
    } catch (ResponseStatusException e) {
      return List.of();
    }
  }

  private void transition(AppointmentJpaEntity entity, String newStatus) {
    if (!VALID_TRANSITIONS.contains(entity.getStatus() + "->" + newStatus)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Transicao de status invalida: " + entity.getStatus() + " -> " + newStatus);
    }
    entity.definirStatus(newStatus);
  }

  private AppointmentJpaEntity loadForCustomer(AuthPrincipal principal, UUID appointmentId) {
    if (principal == null || !"CUSTOMER".equals(principal.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token de gerenciamento necessario");
    }
    if (!principal.userId().equals(appointmentId)) {
      // Nao revela reservas de terceiros
      throw notFound("Reserva nao encontrada");
    }
    return appointments
        .findByIdAndTenantId(appointmentId, principal.tenantId())
        .orElseThrow(() -> notFound("Reserva nao encontrada"));
  }

  private CustomerJpaEntity getOrCreateCustomer(UUID tenantId, String name, String phone) {
    return customers
        .findByTenantIdAndPhoneE164(tenantId, phone)
        .orElseGet(() -> customers.save(CustomerJpaEntity.novo(tenantId, name, phone)));
  }

  private BarberJpaEntity requireActiveBarber(UUID tenantId, UUID barberId) {
    return barbers
        .findByIdAndTenantId(barberId, tenantId)
        .orElseThrow(() -> notFound("Profissional nao encontrado para esta barbearia"));
  }

  private ServiceJpaEntity requireActiveService(UUID tenantId, UUID serviceId) {
    return services
        .findByIdAndTenantId(serviceId, tenantId)
        .orElseThrow(() -> notFound("Servico nao encontrado para esta barbearia"));
  }

  private TenantJpaEntity requireTenant(UUID tenantId) {
    return tenants
        .findById(tenantId)
        .orElseThrow(() -> notFound("Barbearia nao encontrada"));
  }

  private String manageToken(AppointmentJpaEntity entity) {
    return jwt.issue(new AuthPrincipal(entity.getId(), entity.getTenantId(), "CUSTOMER"));
  }

  private AppointmentResponse toResponse(AppointmentJpaEntity entity, String manageToken) {
    return new AppointmentResponse(
        entity.getId(),
        entity.getTenantId(),
        entity.getBarberId(),
        entity.getCustomerId(),
        entity.getServiceId(),
        entity.getStartsAt(),
        entity.getEndsAt(),
        entity.getStatus(),
        entity.getPriceCentsSnapshot(),
        entity.getDurationMinutesSnapshot(),
        manageToken);
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }
}
