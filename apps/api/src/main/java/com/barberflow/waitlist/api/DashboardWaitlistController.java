package com.barberflow.waitlist.api;

import com.barberflow.tenant.application.TenantService;
import com.barberflow.waitlist.application.WaitlistService;
import com.barberflow.waitlist.infrastructure.WaitlistEntryJpaEntity;
import com.barberflow.waitlist.infrastructure.WaitlistEntryRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lista de espera no dashboard (gestor): visualizacao da fila e processamento
 * manual (expira ofertas vencidas e oferece slots livres; worker dedicado
 * entra na Fase 4).
 */
@RestController
@RequestMapping("/api/v1/dashboard/waitlist")
public class DashboardWaitlistController {

  private final WaitlistService waitlist;
  private final WaitlistEntryRepository entries;
  private final TenantService tenants;

  public DashboardWaitlistController(
      WaitlistService waitlist, WaitlistEntryRepository entries, TenantService tenants) {
    this.waitlist = waitlist;
    this.entries = entries;
    this.tenants = tenants;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public List<WaitlistEntryJpaEntity> list() {
    return entries.findByTenantIdOrderByCreatedAtDesc(tenants.requireCurrentTenantId());
  }

  @PostMapping("/process")
  public void process() {
    waitlist.process(tenants.requireCurrentTenantId());
  }
}
