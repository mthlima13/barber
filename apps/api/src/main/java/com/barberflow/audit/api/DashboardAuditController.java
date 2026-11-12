package com.barberflow.audit.api;

import com.barberflow.audit.infrastructure.AuditEventRepository;
import com.barberflow.tenant.application.TenantService;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auditoria no dashboard (gestor): acoes da equipe (spec, secao 6 — auditoria
 * sem dados sensentes desnecessarios).
 */
@RestController
@RequestMapping("/api/v1/dashboard/audit")
public class DashboardAuditController {

  private final AuditEventRepository auditEvents;
  private final TenantService tenants;

  public DashboardAuditController(
      AuditEventRepository auditEvents, TenantService tenants) {
    this.auditEvents = auditEvents;
    this.tenants = tenants;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public List<AuditEventResponse> list() {
    return auditEvents
        .findByTenantIdOrderByCreatedAtDesc(tenants.requireCurrentTenantId())
        .stream()
        .map(AuditEventResponse::from)
        .toList();
  }
}
