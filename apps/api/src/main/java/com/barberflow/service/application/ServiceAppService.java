package com.barberflow.service.application;

import com.barberflow.service.api.ServicePatchRequest;
import com.barberflow.service.api.ServiceRequest;
import com.barberflow.service.api.ServiceResponse;
import com.barberflow.service.infrastructure.ServiceJpaEntity;
import com.barberflow.service.infrastructure.ServiceRepository;
import com.barberflow.tenant.application.TenantService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Casos de uso de servicos da barbearia (CRUD do painel do gestor).
 *
 * <p>TODO(auth): as permissoes por papel (Gestor) entram com o modulo
 * {@code auth}; hoje o acesso exige apenas autenticacao (spec, secao 10).</p>
 */
@Service
public class ServiceAppService {

  private final ServiceRepository services;
  private final TenantService tenants;

  public ServiceAppService(ServiceRepository services, TenantService tenants) {
    this.services = services;
    this.tenants = tenants;
  }

  @Transactional
  public ServiceResponse create(ServiceRequest request) {
    UUID tenantId = tenants.requireDefaultTenantId();
    ServiceJpaEntity entity =
        ServiceJpaEntity.novo(
            tenantId,
            request.name(),
            request.description(),
            request.durationMinutes(),
            request.priceCents());
    return ServiceResponse.from(services.save(entity));
  }

  @Transactional(readOnly = true)
  public List<ServiceResponse> list() {
    UUID tenantId = tenants.requireDefaultTenantId();
    return services.findAllByTenantIdOrderByCreatedAtAsc(tenantId).stream()
        .map(ServiceResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public ServiceResponse get(UUID id) {
    return ServiceResponse.from(findTenantService(id));
  }

  @Transactional
  public ServiceResponse patch(UUID id, ServicePatchRequest request) {
    ServiceJpaEntity entity = findTenantService(id);
    entity.atualizar(
        request.name(), request.description(), request.durationMinutes(), request.priceCents());
    return ServiceResponse.from(entity);
  }

  @Transactional
  public void deactivate(UUID id) {
    ServiceJpaEntity entity = findTenantService(id);
    entity.desativar();
  }

  private ServiceJpaEntity findTenantService(UUID id) {
    UUID tenantId = tenants.requireDefaultTenantId();
    return services
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Servico nao encontrado para este tenant"));
  }
}
