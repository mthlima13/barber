package com.barberflow.appointment.application;

import com.barberflow.appointment.infrastructure.AppointmentExpiryProperties;
import com.barberflow.appointment.infrastructure.AppointmentJpaEntity;
import com.barberflow.appointment.infrastructure.AppointmentRepository;
import com.barberflow.audit.application.AuditService;
import com.barberflow.notification.application.OutboxEvents;
import com.barberflow.notification.application.OutboxService;
import com.barberflow.waitlist.application.WaitlistService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Expiracao automatica de PENDING (spec, linha 137): job periodico que
 * cancela reservas pendentes alem do prazo configurado, liberando o slot
 * para novos agendamentos e para a lista de espera. IDEMPOTENTE: a chave
 * {@code appointment-expired:<id>} e unica na outbox e a transicao
 * PENDING-&gt;CANCELLED so acontece uma vez (varredura pega so PENDING) —
 * reexecucoes do job nao duplicam notificacao nem auditam de novo.
 *
 * <p>Toda a varredura roda em UMA transacao (via {@link TransactionTemplate},
 * para valer tambem no caminho do @Scheduled): status, evento de
 * notificacao, audit e ofertas da lista de espera commitam juntos.</p>
 */
@Component
public class AppointmentExpiryWorker {

  private final AppointmentRepository appointments;
  private final OutboxService outbox;
  private final AuditService audits;
  private final WaitlistService waitlist;
  private final AppointmentExpiryProperties properties;
  private final TransactionTemplate transactions;
  private final Object lock = new Object();

  public AppointmentExpiryWorker(
      AppointmentRepository appointments,
      OutboxService outbox,
      AuditService audits,
      WaitlistService waitlist,
      AppointmentExpiryProperties properties,
      TransactionTemplate transactions) {
    this.appointments = appointments;
    this.outbox = outbox;
    this.audits = audits;
    this.waitlist = waitlist;
    this.properties = properties;
    this.transactions = transactions;
  }

  /** Varredura periodica; desligada enquanto appointment-expiry.enabled=false. */
  @Scheduled(cron = "${barberflow.appointment-expiry.cron:0 */15 * * * *}")
  public void scheduledRun() {
    if (!properties.enabled()) {
      return;
    }
    runOnce();
  }

  /** Uma passada de varredura (global: todos os tenants). Thread-safe. */
  public void runOnce() {
    synchronized (lock) {
      Instant cutoff = Instant.now().minus(Duration.ofHours(properties.pendingTtlHours()));
      transactions.executeWithoutResult(status -> sweep(cutoff));
    }
  }

  private void sweep(Instant cutoff) {
    List<AppointmentJpaEntity> expired =
        appointments.findByStatusAndCreatedAtBefore("PENDING", cutoff);
    for (AppointmentJpaEntity appointment : expired) {
      // PENDING->CANCELLED e transicao valida da maquina de estados (secao 8)
      appointment.definirStatus("CANCELLED");
      appointments.save(appointment);
      outbox.append(
          appointment.getTenantId(),
          OutboxEvents.APPOINTMENT_EXPIRED,
          "appointment-expired:" + appointment.getId(),
          Map.of(
              "appointmentId", appointment.getId(),
              "tenantId", appointment.getTenantId(),
              "customerId", appointment.getCustomerId(),
              "previousStatus", "PENDING",
              "startsAt", appointment.getStartsAt()));
      // Acao do sistema: actor nulo (audit_events.actor_id aceita)
      audits.record(
          appointment.getTenantId(),
          null,
          "APPOINTMENT_EXPIRED",
          AuditService.ENTITY_APPOINTMENT,
          appointment.getId());
      // Slot liberado pode ter inscritos na fila (spec, secao 9.2.3)
      waitlist.process(appointment.getTenantId());
    }
    if (!expired.isEmpty()) {
      org.slf4j.LoggerFactory.getLogger(AppointmentExpiryWorker.class)
          .debug("Job de expiracao cancelou {} reservas pendentes", expired.size());
    }
  }
}
