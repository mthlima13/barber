package com.barberflow.tenant.api;

import com.barberflow.audit.application.AuditService;
import com.barberflow.auth.domain.AuthPrincipal;
import com.barberflow.tenant.application.TenantService;
import com.barberflow.tenant.infrastructure.TenantJpaEntity;
import com.barberflow.tenant.infrastructure.TenantRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuracoes da barbearia no dashboard (gestor). O WhatsApp do gestor
 * alimenta as notificacoes de negocio via outbox (reserva nova, inscricao
 * na fila) — nulo significa notificacao desligada.
 */
@RestController
@RequestMapping("/api/v1/dashboard/settings")
public class DashboardSettingsController {

  private final TenantRepository tenants;
  private final TenantService tenantService;
  private final AuditService audits;

  public DashboardSettingsController(
      TenantRepository tenants, TenantService tenantService, AuditService audits) {
    this.tenants = tenants;
    this.tenantService = tenantService;
    this.audits = audits;
  }

  @GetMapping
  public SettingsResponse get() {
    return SettingsResponse.from(requireTenant());
  }

  @PatchMapping
  public SettingsResponse update(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody UpdateSettingsRequest request) {
    TenantJpaEntity tenant = requireTenant();
    tenant.definirTelefoneGerente(request.managerPhone());
    tenants.save(tenant);
    audits.record(
        tenant.getId(), principal.userId(), "SETTINGS_UPDATED", "SETTINGS", tenant.getId());
    return SettingsResponse.from(tenant);
  }

  private TenantJpaEntity requireTenant() {
    return tenants
        .findById(tenantService.requireCurrentTenantId())
        .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
            HttpStatus.NOT_FOUND, "Barbearia nao encontrada"));
  }

  /** Estado atual das notificacoes ao gestor. */
  public record SettingsResponse(String managerPhone) {

    public static SettingsResponse from(TenantJpaEntity entity) {
      return new SettingsResponse(entity.getManagerPhone());
    }
  }

  /** WhatsApp em E.164 ou null para desligar. */
  public record UpdateSettingsRequest(
      @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "telefone deve estar em formato E.164")
          String managerPhone) {}
}
