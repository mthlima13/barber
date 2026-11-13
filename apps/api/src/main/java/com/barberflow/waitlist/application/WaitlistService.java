package com.barberflow.waitlist.application;

import com.barberflow.appointment.api.AppointmentResponse;
import com.barberflow.appointment.application.AppointmentConflictException;
import com.barberflow.appointment.application.SlotValidator;
import com.barberflow.appointment.infrastructure.AppointmentJpaEntity;
import com.barberflow.appointment.infrastructure.AppointmentRepository;
import com.barberflow.auth.domain.AuthPrincipal;
import com.barberflow.auth.infrastructure.JwtService;
import com.barberflow.service.infrastructure.ServiceJpaEntity;
import com.barberflow.service.infrastructure.ServiceRepository;
import com.barberflow.staff.infrastructure.BarberJpaEntity;
import com.barberflow.staff.infrastructure.BarberRepository;
import com.barberflow.tenant.infrastructure.TenantJpaEntity;
import com.barberflow.tenant.infrastructure.TenantRepository;
import com.barberflow.waitlist.api.CreateWaitlistRequest;
import com.barberflow.waitlist.api.WaitlistDashboardResponse;
import com.barberflow.waitlist.api.WaitlistEntryResponse;
import com.barberflow.waitlist.infrastructure.SlotOfferJpaEntity;
import com.barberflow.waitlist.infrastructure.SlotOfferRepository;
import com.barberflow.waitlist.infrastructure.WaitlistEntryJpaEntity;
import com.barberflow.waitlist.infrastructure.WaitlistEntryRepository;
import com.barberflow.appointment.infrastructure.CustomerJpaEntity;
import com.barberflow.appointment.infrastructure.CustomerRepository;
import com.barberflow.notification.application.OutboxEvents;
import com.barberflow.notification.application.OutboxService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lista de espera (spec, secao 9.2): inscricao por horario especifico e
 * oferta "uma pessoa por vez" com hold transacional.
 *
 * <p>{@link #process(UUID)} expira ofertas vencidas (entrada volta para a
 * fila) e oferta cada slot livre ao primeiro inscrito compativel, em ordem
 * de inscricao. E chamado na mesma transacao do cancelamento; o endpoint de
 * dashboard tambem o dispara (worker dedicado entra na fase 4).</p>
 */
@Service
public class WaitlistService {

  private final TenantRepository tenants;
  private final BarberRepository barbers;
  private final ServiceRepository services;
  private final CustomerRepository customers;
  private final WaitlistEntryRepository entries;
  private final SlotOfferRepository offers;
  private final AppointmentRepository appointments;
  private final SlotValidator slotValidator;
  private final WaitlistExpiryService expiry;
  private final OutboxService outbox;
  private final JwtService jwt;
  private final com.barberflow.privacy.PrivacyProperties privacy;
  private final com.barberflow.metrics.BusinessMetrics metrics;
  private final long offerTtlMinutes;

  public WaitlistService(
      TenantRepository tenants,
      BarberRepository barbers,
      ServiceRepository services,
      CustomerRepository customers,
      WaitlistEntryRepository entries,
      SlotOfferRepository offers,
      AppointmentRepository appointments,
      SlotValidator slotValidator,
      WaitlistExpiryService expiry,
      OutboxService outbox,
      JwtService jwt,
      com.barberflow.privacy.PrivacyProperties privacy,
      com.barberflow.metrics.BusinessMetrics metrics,
      @Value("${barberflow.waitlist.offer-ttl-minutes:5}") long offerTtlMinutes) {
    this.tenants = tenants;
    this.barbers = barbers;
    this.services = services;
    this.customers = customers;
    this.entries = entries;
    this.offers = offers;
    this.appointments = appointments;
    this.slotValidator = slotValidator;
    this.expiry = expiry;
    this.outbox = outbox;
    this.jwt = jwt;
    this.privacy = privacy;
    this.metrics = metrics;
    this.offerTtlMinutes = offerTtlMinutes;
  }

  /** Inscricao para alerta de horario especifico (spec, secao 10). */
  @Transactional
  public WaitlistEntryResponse join(CreateWaitlistRequest request) {
    TenantJpaEntity tenant =
        tenants
            .findBySlug(request.slug())
            .orElseThrow(
                () -> notFound("Barbearia nao encontrada: '" + request.slug() + "'"));

    BarberJpaEntity barber =
        barbers
            .findByIdAndTenantId(request.barberId(), tenant.getId())
            .orElseThrow(() -> notFound("Profissional nao encontrado para esta barbearia"));
    ServiceJpaEntity service =
        services
            .findByIdAndTenantId(request.serviceId(), tenant.getId())
            .orElseThrow(() -> notFound("Servico nao encontrado para esta barbearia"));
    if (!barber.isActive() || !service.isActive()) {
      throw notFound("Profissional ou servico indisponivel nesta barbearia");
    }

    if (request.startsAt().isBefore(Instant.now())) {
      throw badRequest("Horario da inscricao ja passou");
    }
    // LGPD (spec, linha 223): inscricao na lista de espera tambem coleta
    // dados pessoais — exige aceite da politica de privacidade.
    if (!request.customer().consented()) {
      throw badRequest("E necessario aceitar a politica de privacidade");
    }
    Instant endsAt = request.startsAt().plus(Duration.ofMinutes(service.getDurationMinutes()));
    slotValidator.validateWindow(tenant, barber.getId(), request.startsAt(), endsAt);

    CustomerJpaEntity customer =
        customers
            .findByTenantIdAndPhoneE164(tenant.getId(), request.customer().phone())
            .orElseGet(
                () ->
                    customers.save(
                        CustomerJpaEntity.novo(
                            tenant.getId(), request.customer().name(), request.customer().phone())));
    customer.registrarConsentimento(
        privacy.policyVersion() != null ? privacy.policyVersion() : "1.0", "web-waitlist");

    if (entries.existsByTenantIdAndCustomerIdAndBarberIdAndDesiredStartAtAndStatusIn(
        tenant.getId(),
        customer.getId(),
        barber.getId(),
        request.startsAt(),
        List.of("WAITING", "OFFERED"))) {
      throw conflict("Cliente ja esta na lista de espera para este horario");
    }

    WaitlistEntryJpaEntity saved =
        entries.save(
            WaitlistEntryJpaEntity.novo(
                tenant.getId(),
                barber.getId(),
                customer.getId(),
                service.getId(),
                request.startsAt(),
                endsAt));
    notifyManager(
        tenant,
        OutboxEvents.MANAGER_WAITLIST_JOINED,
        "manager-waitlist-joined:" + saved.getId(),
        java.util.Map.of(
            "audience", "manager",
            "entryId", saved.getId(),
            "customerId", customer.getId(),
            "startsAt", request.startsAt()));
    return toResponse(saved, true);
  }

  /**
   * Notificacao ao gestor (WhatsApp do tenant em manager_phone): so e
   * agendada quando o telefone esta configurado; entrega e do worker.
   */
  private void notifyManager(
      TenantJpaEntity tenant,
      String eventType,
      String idempotencyKey,
      java.util.Map<String, Object> payload) {
    String phone = tenant.getManagerPhone();
    if (phone == null || phone.isBlank()) {
      return;
    }
    outbox.append(tenant.getId(), eventType, idempotencyKey, payload);
  }

  /**
   * Expira ofertas vencidas e oferece slots livres ao primeiro inscrito
   * compativel (FIFO, spec, secao 9.2.3). Roda na transacao do cancelamento
   * e no endpoint de processamento do dashboard.
   */
  @Transactional
  public void process(UUID tenantId) {
    Instant now = Instant.now();
    for (SlotOfferJpaEntity offer : offers.findByTenantIdAndStatus(tenantId, "PENDING")) {
      if (offer.expirada(now)) {
        offer.expirar();
        metrics.waitlistExpired();
        entries
            .findById(offer.getWaitlistEntryId())
            .filter(entry -> "OFFERED".equals(entry.getStatus()))
            .ifPresent(WaitlistEntryJpaEntity::voltarParaFila);
      }
    }

    TenantJpaEntity tenant =
        tenants.findById(tenantId).orElseThrow(() -> notFound("Barbearia nao encontrada"));
    for (WaitlistEntryJpaEntity entry :
        entries.findByTenantIdAndStatusOrderByUpdatedAtAsc(tenantId, "WAITING")) {
      try {
        slotValidator.validateFree(
            tenant,
            entry.getBarberId(),
            entry.getServiceId(),
            entry.getDesiredStartAt(),
            entry.getDesiredEndAt(),
            null,
            null);
      } catch (AppointmentConflictException | ResponseStatusException e) {
        // Slot ocupado/bloqueado ou expediente mudou: segue para o proximo.
        continue;
      }
      Instant expiresAt = Instant.now().plus(Duration.ofMinutes(offerTtlMinutes));
      UUID offerId = UUID.randomUUID();
      SlotOfferJpaEntity offer =
          offers.save(
              SlotOfferJpaEntity.novoComId(
                  offerId,
                  tenantId,
                  entry.getId(),
                  entry.getDesiredStartAt(),
                  entry.getDesiredEndAt(),
                  sha256(jwt.offerToken(offerId)),
                  expiresAt));
      entry.oferecer(expiresAt);
      metrics.waitlistOffered();
      // Evento de entrega na MESMA transacao da oferta (spec, secao 9.2.4);
      // worker de WhatsApp entra na Fase 4. Token nunca vai para o payload.
      outbox.append(
          tenantId,
          OutboxEvents.WAITLIST_SLOT_AVAILABLE,
          "waitlist-offer:" + offerId,
          java.util.Map.of(
              "offerId", offerId,
              "entryId", entry.getId(),
              "customerId", entry.getCustomerId(),
              "startsAt", entry.getDesiredStartAt(),
              "endsAt", entry.getDesiredEndAt(),
              "expiresAt", expiresAt));
    }
  }

  /** Consulta pelo proprio link seguro (token de gerenciamento). */
  @Transactional(readOnly = true)
  public WaitlistEntryResponse forCustomer(AuthPrincipal principal, UUID entryId) {
    WaitlistEntryJpaEntity entry = loadOwnedEntry(principal, entryId);
    return toResponse(entry, true);
  }

  /** Sair da lista (spec, secao 10): token de gestao da propria inscricao. */
  @Transactional
  public WaitlistEntryResponse leave(AuthPrincipal principal, UUID entryId) {
    WaitlistEntryJpaEntity entry = loadOwnedEntry(principal, entryId);
    switch (entry.getStatus()) {
      case "WAITING", "OFFERED", "EXPIRED" -> {
        offers
            .findByTenantIdAndWaitlistEntryIdAndStatus(entry.getTenantId(), entry.getId(), "PENDING")
            .forEach(SlotOfferJpaEntity::expirar);
        entry.cancelar();
      }
      case "FULFILLED" -> throw conflict("Inscricao ja foi transformada em reserva");
      default -> {
        // CANCELLED: idempotente
      }
    }
    return toResponse(entry, true);
  }

  /**
   * Aceite da oferta (spec, secao 9.2.5): valida token, expiracao e que o
   * horario segue livre; transforma o hold em reserva PENDING na mesma
   * transacao. Nunca confirmar somente pelo clique sem revalidar.
   */
  @Transactional
  public AppointmentResponse accept(String token) {
    if (token == null || token.isBlank()) {
      throw notFound("Oferta nao encontrada");
    }
    SlotOfferJpaEntity offer =
        offers
            .findByTokenHash(sha256(token))
            .orElseThrow(() -> notFound("Oferta nao encontrada"));
    WaitlistEntryJpaEntity entry =
        entries
            .findById(offer.getWaitlistEntryId())
            .orElseThrow(() -> notFound("Oferta nao encontrada"));
    TenantJpaEntity tenant =
        tenants.findById(offer.getTenantId()).orElseThrow(() -> notFound("Barbearia nao encontrada"));

    if (!"PENDING".equals(offer.getStatus())
        || !"OFFERED".equals(entry.getStatus())
        || offer.expirada(Instant.now())
        || offer.getStartsAt().isBefore(Instant.now())) {
      // Transacao propria: o rollback do aceite nao pode desfazer a expiracao.
      expiry.expirarOfertaVoltarFila(offer.getId(), entry.getId());
      throw conflict("Oferta expirada ou indisponivel");
    }

    ServiceJpaEntity service =
        services
            .findByIdAndTenantId(entry.getServiceId(), entry.getTenantId())
            .orElseThrow(() -> notFound("Servico nao encontrado para esta barbearia"));

    try {
      slotValidator.validateFree(
          tenant,
          entry.getBarberId(),
          entry.getServiceId(),
          offer.getStartsAt(),
          offer.getEndsAt(),
          null,
          // O hold proprio nao e conflito: ele sera consumido pelo aceite.
          offer.getId());
    } catch (AppointmentConflictException | ResponseStatusException e) {
      // Alguem ocupou/bloqueou enquanto a oferta estava aberta.
      expiry.expirarOfertaVoltarFila(offer.getId(), entry.getId());
      throw conflict("Horario nao esta mais disponivel");
    }

    AppointmentJpaEntity saved =
        appointments.save(
            AppointmentJpaEntity.novo(
                tenant.getId(),
                entry.getBarberId(),
                entry.getCustomerId(),
                entry.getServiceId(),
                offer.getStartsAt(),
                offer.getEndsAt(),
                service.getPriceCents(),
                service.getDurationMinutes(),
                null));
    offer.aceitar();
    entry.cumprir();
    metrics.waitlistAccepted();
    // Reserva criada via lista de espera: evento na mesma transacao (9.1.5).
    outbox.append(
        tenant.getId(),
        OutboxEvents.APPOINTMENT_CREATED,
        "appointment-created:offer-" + offer.getId(),
        java.util.Map.of(
            "appointmentId", saved.getId(),
            "tenantId", tenant.getId(),
            "barberId", entry.getBarberId(),
            "customerId", entry.getCustomerId(),
            "serviceId", entry.getServiceId(),
            "startsAt", offer.getStartsAt(),
            "endsAt", offer.getEndsAt(),
            "status", "PENDING",
            "source", "WAITLIST"));
    notifyManager(
        tenant,
        OutboxEvents.MANAGER_APPOINTMENT_CREATED,
        "manager-appointment-created:" + saved.getId(),
        java.util.Map.of(
            "audience", "manager",
            "appointmentId", saved.getId(),
            "customerId", entry.getCustomerId(),
            "startsAt", offer.getStartsAt(),
            "status", "PENDING",
            "source", "WAITLIST"));

    return new AppointmentResponse(
        saved.getId(),
        saved.getTenantId(),
        saved.getBarberId(),
        saved.getCustomerId(),
        saved.getServiceId(),
        saved.getStartsAt(),
        saved.getEndsAt(),
        saved.getStatus(),
        saved.getPriceCentsSnapshot(),
        saved.getDurationMinutesSnapshot(),
        manageToken(saved));
  }

  // ------------------------------------------------------------------ internals

  private WaitlistEntryJpaEntity loadOwnedEntry(AuthPrincipal principal, UUID entryId) {
    if (principal == null || !"CUSTOMER".equals(principal.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token de gerenciamento necessario");
    }
    if (!principal.userId().equals(entryId)) {
      // Nao revela inscricoes de terceiros
      throw notFound("Inscricao nao encontrada");
    }
    return entries
        .findByIdAndTenantId(entryId, principal.tenantId())
        .orElseThrow(() -> notFound("Inscricao nao encontrada"));
  }

  private String manageToken(AppointmentJpaEntity entity) {
    return jwt.issue(new AuthPrincipal(entity.getId(), entity.getTenantId(), "CUSTOMER"));
  }

  /**
   * Fila para o dashboard (gestor): cliente resolvido em nome/telefone e
   * oferta corrente com o slot ofertado — a pagina nao resolve ids nos
   * catalogos para mostrar quem esta na fila. Sem tokens de cliente.
   */
  @Transactional(readOnly = true)
  public List<WaitlistDashboardResponse> dashboardList(UUID tenantId) {
    return entries.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
        .map(this::toDashboardResponse)
        .toList();
  }

  private WaitlistDashboardResponse toDashboardResponse(WaitlistEntryJpaEntity entry) {
    CustomerJpaEntity customer = customers.findById(entry.getCustomerId()).orElse(null);
    WaitlistDashboardResponse.OfferView offerView = null;
    if ("OFFERED".equals(entry.getStatus())) {
      List<SlotOfferJpaEntity> pending =
          offers.findByTenantIdAndWaitlistEntryIdAndStatus(
              entry.getTenantId(), entry.getId(), "PENDING");
      if (!pending.isEmpty()) {
        SlotOfferJpaEntity offer = pending.get(0);
        offerView =
            new WaitlistDashboardResponse.OfferView(
                offer.getId(), offer.getStartsAt(), offer.getEndsAt(), offer.getExpiresAt());
      }
    }
    return new WaitlistDashboardResponse(
        entry.getId(),
        entry.getBarberId(),
        entry.getServiceId(),
        customer == null ? "Cliente removido" : customer.getName(),
        customer == null ? null : customer.getPhoneE164(),
        entry.getDesiredStartAt(),
        entry.getDesiredEndAt(),
        entry.getStatus(),
        entry.getCreatedAt(),
        entry.getExpiresAt(),
        offerView);
  }

  private WaitlistEntryResponse toResponse(WaitlistEntryJpaEntity entry, boolean withToken) {
    WaitlistEntryResponse.OfferView offerView = null;
    if ("OFFERED".equals(entry.getStatus())) {
      List<SlotOfferJpaEntity> pending =
          offers.findByTenantIdAndWaitlistEntryIdAndStatus(
              entry.getTenantId(), entry.getId(), "PENDING");
      if (!pending.isEmpty()) {
        SlotOfferJpaEntity offer = pending.get(0);
        offerView =
            new WaitlistEntryResponse.OfferView(
                offer.getId(),
                offer.getStartsAt(),
                offer.getEndsAt(),
                offer.getExpiresAt(),
                jwt.offerToken(offer.getId()));
      }
    }
    return new WaitlistEntryResponse(
        entry.getId(),
        entry.getTenantId(),
        entry.getBarberId(),
        entry.getServiceId(),
        entry.getDesiredStartAt(),
        entry.getDesiredEndAt(),
        entry.getStatus(),
        entry.getExpiresAt(),
        offerView,
        withToken ? jwt.issue(new AuthPrincipal(entry.getId(), entry.getTenantId(), "CUSTOMER"))
            : null);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao calcular hash de token", e);
    }
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }

  private ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }
}
