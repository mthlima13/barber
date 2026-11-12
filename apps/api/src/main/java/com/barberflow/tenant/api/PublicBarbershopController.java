package com.barberflow.tenant.api;

import com.barberflow.service.infrastructure.ServiceRepository;
import com.barberflow.staff.infrastructure.BarberRepository;
import com.barberflow.tenant.infrastructure.TenantJpaEntity;
import com.barberflow.tenant.infrastructure.TenantRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Catalogo publico da barbearia (spec, linhas 177-179): a pagina de
 * agendamento descobre servicos e profissionais pelo slug antes de
 * consultar a availability. Apenas entidades ativas sao visiveis.
 *
 * <p>Slug desconhecido responde 404 (problem+json). Rate limit de leitura
 * publica aplicado pelo {@link com.barberflow.shared.ratelimit.RateLimitFilter}.</p>
 */
@RestController
@RequestMapping("/api/v1/public/barbershops")
public class PublicBarbershopController {

  private final TenantRepository tenants;
  private final ServiceRepository services;
  private final BarberRepository barbers;

  public PublicBarbershopController(
      TenantRepository tenants, ServiceRepository services, BarberRepository barbers) {
    this.tenants = tenants;
    this.services = services;
    this.barbers = barbers;
  }

  /** Dados publicos da barbearia (spec, linha 177). */
  @GetMapping("/{slug}")
  @Transactional(readOnly = true)
  public PublicBarbershopResponse bySlug(@PathVariable String slug) {
    return PublicBarbershopResponse.from(requireTenant(slug));
  }

  /** Servicos ativos (spec, linha 178). */
  @GetMapping("/{slug}/services")
  @Transactional(readOnly = true)
  public List<PublicServiceResponse> services(@PathVariable String slug) {
    return services
        .findAllByTenantIdAndActiveTrueOrderByCreatedAtAsc(requireTenant(slug).getId())
        .stream()
        .map(PublicServiceResponse::from)
        .toList();
  }

  /** Profissionais ativos (spec, linha 179). */
  @GetMapping("/{slug}/barbers")
  @Transactional(readOnly = true)
  public List<PublicBarberResponse> barbers(@PathVariable String slug) {
    return barbers
        .findAllByTenantIdAndActiveTrueOrderByCreatedAtAsc(requireTenant(slug).getId())
        .stream()
        .map(PublicBarberResponse::from)
        .toList();
  }

  /** Slug nao existe -> 404 (nao ha vazamento de tenants por enumeracao). */
  private TenantJpaEntity requireTenant(String slug) {
    return tenants
        .findBySlug(slug)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Barbearia nao encontrada"));
  }
}
