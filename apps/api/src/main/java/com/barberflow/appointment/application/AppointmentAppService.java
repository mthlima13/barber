package com.barberflow.appointment.application;

import com.barberflow.appointment.api.AppointmentResponse;
import com.barberflow.appointment.api.CreateAppointmentRequest;
import com.barberflow.appointment.infrastructure.AppointmentJpaEntity;
import com.barberflow.appointment.infrastructure.AppointmentRepository;
import com.barberflow.appointment.infrastructure.CustomerJpaEntity;
import com.barberflow.appointment.infrastructure.CustomerRepository;
import com.barberflow.audit.application.AuditService;
import com.barberflow.auth.domain.AuthPrincipal;
import com.barberflow.auth.infrastructure.JwtService;
import com.barberflow.notification.application.OutboxEvents;
import com.barberflow.notification.application.OutboxService;
import com.barberflow.service.infrastructure.ServiceJpaEntity;
import com.barberflow.service.infrastructure.ServiceRepository;
import com.barberflow.staff.infrastructure.BarberJpaEntity;
import com.barberflow.staff.infrastructure.BarberRepository;
import com.barberflow.tenant.infrastructure.TenantJpaEntity;
import com.barberflow.tenant.infrastructure.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
  private final com.barberflow.availability.infrastructure.AvailabilityJdbcRepository
      availabilityJdbc;
  private final SlotValidator slotValidator;
  private final com.barberflow.waitlist.application.WaitlistService waitlist;
  private final OutboxService outbox;
  private final AuditService audits;
  private final JwtService jwt;
  private final com.barberflow.privacy.PrivacyProperties privacy;
  private final com.barberflow.metrics.BusinessMetrics metrics;

  public AppointmentAppService(
      TenantRepository tenants,
      BarberRepository barbers,
      ServiceRepository services,
      CustomerRepository customers,
      AppointmentRepository appointments,
      com.barberflow.availability.infrastructure.AvailabilityJdbcRepository availabilityJdbc,
      SlotValidator slotValidator,
      com.barberflow.waitlist.application.WaitlistService waitlist,
      OutboxService outbox,
      AuditService audits,
      JwtService jwt,
      com.barberflow.privacy.PrivacyProperties privacy,
      com.barberflow.metrics.BusinessMetrics metrics) {
    this.tenants = tenants;
    this.barbers = barbers;
    this.services = services;
    this.customers = customers;
    this.appointments = appointments;
    this.availabilityJdbc = availabilityJdbc;
    this.slotValidator = slotValidator;
    this.waitlist = waitlist;
    this.outbox = outbox;
    this.audits = audits;
    this.jwt = jwt;
    this.privacy = privacy;
    this.metrics = metrics;
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

    // LGPD (spec, linha 223): sem aceite da politica de privacidade, a
    // coleta de dados pessoais nao acontece (base legal: consentimento).
    if (!request.customer().consented()) {
      throw badRequest("E necessario aceitar a politica de privacidade");
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
    try {
      validateSlot(tenant, barber, service, request.startsAt(), endsAt, null);
    } catch (AppointmentConflictException e) {
      // Tentativa de reserva em horario ocupado: 409 com alternativas
      // (o uso do validateFree pela fila de espera nao conta aqui)
      metrics.appointmentConflict();
      throw e;
    }

    CustomerJpaEntity customer =
        getOrCreateCustomer(tenant.getId(), request.customer().name(), request.customer().phone());
    customer.registrarConsentimento(
        privacy.policyVersion() != null ? privacy.policyVersion() : "1.0", "web-booking");

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
      metrics.appointmentConflict();
      ZoneId zone = ZoneId.of(tenant.getTimezone());
      throw new AppointmentConflictException(
          "Horario indisponivel para este profissional",
          slotValidator.alternatives(
              tenant.getId(),
              tenant.getTimezone(),
              barber.getId(),
              service.getId(),
              request.startsAt().atZone(zone).toLocalDate()));
    }

    // Evento de notificacao na MESMA transacao (spec, secao 9.1.5); chave de
    // idempotencia evita notificacao duplicada em replay.
    metrics.appointmentCreated();
    outbox.append(
        tenant.getId(),
        OutboxEvents.APPOINTMENT_CREATED,
        idempotencyKey == null || idempotencyKey.isBlank()
            ? null
            : "appointment-created:" + idempotencyKey,
        java.util.Map.of(
            "appointmentId", saved.getId(),
            "tenantId", tenant.getId(),
            "barberId", barber.getId(),
            "customerId", customer.getId(),
            "serviceId", service.getId(),
            "startsAt", request.startsAt(),
            "endsAt", endsAt,
            "status", "PENDING",
            "source", "PUBLIC"));

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
    String previousStatus = entity.getStatus();
    transition(entity, "CANCELLED");
    // Evento de notificacao na MESMA transacao (spec, secao 9.2.2).
    outbox.append(
        entity.getTenantId(),
        OutboxEvents.APPOINTMENT_CANCELLED,
        null,
        java.util.Map.of(
            "appointmentId", entity.getId(),
            "tenantId", entity.getTenantId(),
            "previousStatus", previousStatus,
            "startsAt", entity.getStartsAt(),
            "status", "CANCELLED"));
    // Horario liberado fica elegivel para a lista de espera (spec, secao 9.2.3);
    // ofertas registradas na mesma transacao.
    waitlist.process(entity.getTenantId());
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
    Instant previousStartsAt = entity.getStartsAt();
    entity.remarcar(newStartsAt, newEndsAt);
    outbox.append(
        entity.getTenantId(),
        OutboxEvents.APPOINTMENT_RESCHEDULED,
        null,
        java.util.Map.of(
            "appointmentId", entity.getId(),
            "tenantId", entity.getTenantId(),
            "previousStartsAt", previousStartsAt,
            "startsAt", newStartsAt,
            "endsAt", newEndsAt,
            "status", entity.getStatus()));
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

  /**
   * Atualizacao de status pelo gestor, respeitando a maquina de estados.
   * Toda transicao e auditada com o autor (spec, secao 6); CANCELLED tambem
   * emite evento de notificacao na outbox.
   */
  @Transactional
  public AppointmentResponse updateStatus(
      UUID tenantId, UUID appointmentId, String newStatus, AuthPrincipal actor) {
    AppointmentJpaEntity entity =
        appointments
            .findByIdAndTenantId(appointmentId, tenantId)
            .orElseThrow(() -> notFound("Reserva nao encontrada para esta barbearia"));
    String previousStatus = entity.getStatus();
    transition(entity, newStatus);
    audits.record(
        tenantId,
        actor != null ? actor.userId() : null,
        "APPOINTMENT_" + newStatus,
        AuditService.ENTITY_APPOINTMENT,
        entity.getId());
    // So CANCELLED libera o intervalo (COMPLETED/NO_SHOW o consumiram).
    if ("CANCELLED".equals(newStatus)) {
      outbox.append(
          tenantId,
          OutboxEvents.APPOINTMENT_CANCELLED,
          null,
          java.util.Map.of(
              "appointmentId", entity.getId(),
              "tenantId", tenantId,
              "previousStatus", previousStatus,
              "startsAt", entity.getStartsAt(),
              "status", "CANCELLED"));
      waitlist.process(tenantId);
    }
    return toResponse(entity, null);
  }

  /**
   * Agenda do PROPRIO profissional (papel BARBER, spec linha 218/361):
   * autorizacao por recurso — o perfil vem do token (user_id), nunca do
   * cliente. Sem perfil vinculado, negado.
   */
  @Transactional(readOnly = true)
  public List<AppointmentResponse> barberAgenda(AuthPrincipal principal, LocalDate date) {
    BarberJpaEntity barber = requireLinkedBarber(principal);
    TenantJpaEntity tenant = requireTenant(principal.tenantId());
    ZoneId zone = ZoneId.of(tenant.getTimezone());
    Instant dayStart = date.atStartOfDay(zone).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

    return appointments
        .findByTenantIdAndBarberIdAndStartsAtBetweenOrderByStartsAtAsc(
            principal.tenantId(), barber.getId(), dayStart, dayEnd)
        .stream()
        .map(entity -> toResponse(entity, null))
        .toList();
  }

  /** Transicao de status na PROPRIA agenda do profissional (linha 361). */
  @Transactional
  public AppointmentResponse barberUpdateStatus(
      AuthPrincipal principal, UUID appointmentId, String newStatus) {
    BarberJpaEntity barber = requireLinkedBarber(principal);
    AppointmentJpaEntity entity =
        appointments
            .findByIdAndTenantId(appointmentId, principal.tenantId())
            .orElseThrow(() -> notFound("Reserva nao encontrada"));
    if (!barber.getId().equals(entity.getBarberId())) {
      // Agenda de outro profissional: 404 (nao revela existencia)
      throw notFound("Reserva nao encontrada");
    }
    String previousStatus = entity.getStatus();
    transition(entity, newStatus);
    audits.record(
        principal.tenantId(),
        principal.userId(),
        "APPOINTMENT_" + newStatus,
        AuditService.ENTITY_APPOINTMENT,
        entity.getId());
    if ("CANCELLED".equals(newStatus)) {
      outbox.append(
          principal.tenantId(),
          OutboxEvents.APPOINTMENT_CANCELLED,
          null,
          java.util.Map.of(
              "appointmentId", entity.getId(),
              "tenantId", principal.tenantId(),
              "previousStatus", previousStatus,
              "startsAt", entity.getStartsAt(),
              "status", "CANCELLED"));
      waitlist.process(principal.tenantId());
    }
    return toResponse(entity, null);
  }

  // ------------------------------------------------------------------ internals

  /** Perfil BARBER vinculado a conta autenticada; sem vinculo, negado. */
  private BarberJpaEntity requireLinkedBarber(AuthPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token de acesso necessario");
    }
    return barbers
        .findByTenantIdAndUserId(principal.tenantId(), principal.userId())
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Usuario sem perfil de profissional nesta barbearia"));
  }

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
    slotValidator.validateFree(
        tenant, barberId, service.getId(), startsAt, endsAt, ignoreAppointmentId, null);
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
