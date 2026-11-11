package com.barberflow.staff.application;

import com.barberflow.service.api.ServiceResponse;
import com.barberflow.service.infrastructure.ServiceRepository;
import com.barberflow.staff.api.BarberPatchRequest;
import com.barberflow.staff.api.BarberRequest;
import com.barberflow.staff.api.BarberResponse;
import com.barberflow.staff.infrastructure.BarberJpaEntity;
import com.barberflow.staff.infrastructure.BarberRepository;
import com.barberflow.staff.infrastructure.StaffJdbcRepository;
import com.barberflow.tenant.application.TenantService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Casos de uso de profissionais da barbearia (painel do gestor).
 *
 * <p>TODO(auth): permissoes por papel (Gestor) entram com o modulo auth
 * (spec, secao 10). TODO(modulos): restringir a dependencia direta do
 * repositorio de services quando as fronteiras do monolito modular
 * amadurecerem.</p>
 */
@Service
public class BarberAppService {

  private final BarberRepository barbers;
  private final StaffJdbcRepository staffJdbc;
  private final ServiceRepository services;
  private final TenantService tenants;

  public BarberAppService(
      BarberRepository barbers,
      StaffJdbcRepository staffJdbc,
      ServiceRepository services,
      TenantService tenants) {
    this.barbers = barbers;
    this.staffJdbc = staffJdbc;
    this.services = services;
    this.tenants = tenants;
  }

  @Transactional
  public BarberResponse create(BarberRequest request) {
    UUID tenantId = tenants.requireDefaultTenantId();
    if (request.userId() != null && !staffJdbc.userExists(request.userId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Usuario vinculado nao existe: " + request.userId());
    }
    BarberJpaEntity entity =
        BarberJpaEntity.novo(tenantId, request.userId(), request.displayName());
    return BarberResponse.from(barbers.save(entity));
  }

  @Transactional(readOnly = true)
  public List<BarberResponse> list() {
    UUID tenantId = tenants.requireDefaultTenantId();
    return barbers.findAllByTenantIdOrderByCreatedAtAsc(tenantId).stream()
        .map(BarberResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public BarberResponse get(UUID id) {
    return BarberResponse.from(findTenantBarber(id));
  }

  @Transactional
  public BarberResponse patch(UUID id, BarberPatchRequest request) {
    BarberJpaEntity entity = findTenantBarber(id);
    entity.atualizar(request.displayName());
    if (request.active() != null) {
      entity.definirAtivo(request.active());
    }
    return BarberResponse.from(entity);
  }

  @Transactional
  public void deactivate(UUID id) {
    BarberJpaEntity entity = findTenantBarber(id);
    entity.definirAtivo(false);
  }

  @Transactional(readOnly = true)
  public List<ServiceResponse> listEnabledServices(UUID barberId) {
    BarberJpaEntity barber = findTenantBarber(barberId);
    return staffJdbc.findEnabledServiceIds(barber.getTenantId(), barberId).stream()
        .map(serviceId -> services.findByIdAndTenantId(serviceId, barber.getTenantId()))
        .flatMap(java.util.Optional::stream)
        .map(ServiceResponse::from)
        .toList();
  }

  /** Substitui o conjunto de servicos habilitados para o profissional. */
  @Transactional
  public List<ServiceResponse> enableServices(UUID barberId, List<UUID> serviceIds) {
    BarberJpaEntity barber = findTenantBarber(barberId);
    List<UUID> distinct =
        new ArrayList<>(new LinkedHashSet<>(serviceIds == null ? List.of() : serviceIds));

    List<ServiceResponse> result = new ArrayList<>();
    for (UUID serviceId : distinct) {
      var entity =
          services
              .findByIdAndTenantId(serviceId, barber.getTenantId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.BAD_REQUEST,
                          "Servico invalido para este tenant: " + serviceId));
      result.add(ServiceResponse.from(entity));
    }

    staffJdbc.replaceEnabledServices(barber.getTenantId(), barberId, distinct);
    return result;
  }

  private BarberJpaEntity findTenantBarber(UUID id) {
    UUID tenantId = tenants.requireDefaultTenantId();
    return barbers
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Barbeiro nao encontrado para este tenant"));
  }
}
