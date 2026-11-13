package com.barberflow.notification.application;

import com.barberflow.appointment.infrastructure.AppointmentJpaEntity;
import com.barberflow.appointment.infrastructure.AppointmentRepository;
import com.barberflow.notification.infrastructure.ReminderProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lembrete 24h (spec, secao 9.1): job periodico que varre reservas
 * PENDING/CONFIRMED dentro da janela de antecedencia e agenda a mensagem na
 * outbox. IDEMPOTENTE por construcao: a chave
 * {@code appointment-reminder:<id>} e unica no banco, entao reexecucoes do
 * job (ou instancias multiplas em producao) nunca geram uma segunda
 * mensagem para a mesma reserva. A entrega real e do {@link OutboxWorker};
 * se a reserva for cancelada apos o agendamento, o evento segue o mesmo
 * ciclo de entrega dos demais (janela de corrida aceita no MVP).
 */
@Component
public class ReminderWorker {

  private final AppointmentRepository appointments;
  private final OutboxService outbox;
  private final ReminderProperties properties;
  private final Object lock = new Object();

  public ReminderWorker(
      AppointmentRepository appointments, OutboxService outbox, ReminderProperties properties) {
    this.appointments = appointments;
    this.outbox = outbox;
    this.properties = properties;
  }

  /** Varredura periodica; desligada enquanto reminders.enabled=false. */
  @Scheduled(cron = "${barberflow.reminders.cron:0 */15 * * * *}")
  public void scheduledRun() {
    if (!properties.enabled()) {
      return;
    }
    runOnce();
  }

  /** Uma passada de varredura. Thread-safe. */
  public void runOnce() {
    synchronized (lock) {
      Instant now = Instant.now();
      List<AppointmentJpaEntity> due =
          appointments.findByStatusInAndStartsAtBetween(
              List.of("PENDING", "CONFIRMED"),
              now,
              now.plus(Duration.ofHours(properties.windowHours())));
      for (AppointmentJpaEntity appointment : due) {
        outbox.append(
            appointment.getTenantId(),
            OutboxEvents.APPOINTMENT_REMINDER,
            "appointment-reminder:" + appointment.getId(),
            Map.of(
                "appointmentId", appointment.getId(),
                "tenantId", appointment.getTenantId(),
                "customerId", appointment.getCustomerId(),
                "startsAt", appointment.getStartsAt(),
                "status", appointment.getStatus()));
      }
      if (!due.isEmpty()) {
        org.slf4j.LoggerFactory.getLogger(ReminderWorker.class)
            .debug("Job de lembrete agendou {} mensagens", due.size());
      }
    }
  }
}
