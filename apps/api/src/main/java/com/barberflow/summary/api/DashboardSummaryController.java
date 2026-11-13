package com.barberflow.summary.api;

import com.barberflow.summary.application.SummaryService;
import com.barberflow.tenant.application.TenantService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cartoes resumo do painel (gestor): visao do dia em uma chamada. */
@RestController
@RequestMapping("/api/v1/dashboard/summary")
public class DashboardSummaryController {

  private final SummaryService summaries;
  private final TenantService tenants;

  public DashboardSummaryController(SummaryService summaries, TenantService tenants) {
    this.summaries = summaries;
    this.tenants = tenants;
  }

  @GetMapping
  public SummaryResponse summary(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    // Sem data: hoje no fuso da barbearia (resolvido no servico)
    return summaries.summary(tenants.requireCurrentTenantId(), date);
  }
}
