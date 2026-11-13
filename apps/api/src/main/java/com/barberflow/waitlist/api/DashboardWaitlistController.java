package com.barberflow.waitlist.api;

import com.barberflow.tenant.application.TenantService;
import com.barberflow.waitlist.application.WaitlistService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lista de espera no dashboard (gestor): visualizacao da fila com cliente
 * resolvido e processamento manual (expira ofertas vencidas e oferece
 * slots livres; worker dedicado entra na Fase 4).
 */
@RestController
@RequestMapping("/api/v1/dashboard/waitlist")
public class DashboardWaitlistController {

  private final WaitlistService waitlist;
  private final TenantService tenants;

  public DashboardWaitlistController(WaitlistService waitlist, TenantService tenants) {
    this.waitlist = waitlist;
    this.tenants = tenants;
  }

  @GetMapping
  public List<WaitlistDashboardResponse> list() {
    return waitlist.dashboardList(tenants.requireCurrentTenantId());
  }

  @PostMapping("/process")
  public void process() {
    waitlist.process(tenants.requireCurrentTenantId());
  }
}

