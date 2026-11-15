package com.barberflow.notification.api;

import com.barberflow.notification.infrastructure.OutboxEventJpaEntity;
import com.barberflow.notification.infrastructure.OutboxRepository;
import com.barberflow.tenant.application.TenantService;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outbox no dashboard (gestor): fila de eventos de notificacao (observabilidade;
 * spec, linha 293: tamanho/idade da outbox). Envio real pelo worker da Fase 4.
 */
@RestController
@RequestMapping("/api/v1/dashboard/outbox")
public class DashboardOutboxController {

  private final OutboxRepository outbox;
  private final TenantService tenants;

  public DashboardOutboxController(OutboxRepository outbox, TenantService tenants) {
    this.outbox = outbox;
    this.tenants = tenants;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public List<OutboxEventResponse> list() {
    // Paginada: o historico cresce sem freio em producao — o painel le
    // apenas os 100 eventos mais recentes (spec, linha 293)
    return outbox
        .findByTenantIdOrderByCreatedAtDesc(
            tenants.requireCurrentTenantId(), org.springframework.data.domain.PageRequest.of(0, 100))
        .getContent()
        .stream()
        .map(OutboxEventResponse::from)
        .toList();
  }
}
