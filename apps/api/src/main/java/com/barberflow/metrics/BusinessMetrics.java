package com.barberflow.metrics;

import com.barberflow.notification.infrastructure.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Metricas de negocio (spec, linha 293): reservas criadas/canceladas,
 * conflito de agenda, funil da lista de espera (ofertas/aceites/expiradas),
 * falhas de entrega e saude da outbox (tamanho/idade da fila).
 *
 * <p>P95 e 5xx vem das metricas padrao do Spring (http.server.requests);
 * o Prometheus expoe tudo em /actuator/prometheus.</p>
 */
@Component
public class BusinessMetrics {

  private final MeterRegistry registry;
  private final OutboxRepository outbox;

  public BusinessMetrics(MeterRegistry registry, OutboxRepository outbox) {
    this.registry = registry;
    this.outbox = outbox;

    // Saude da outbox: avaliadas no scrape (tabela pequena no MVP)
    Gauge.builder(
            "barberflow.outbox.pending", () -> outbox.countByStatus("PENDING"))
        .description("Eventos de notificacao aguardando entrega")
        .register(registry);
    Gauge.builder(
            "barberflow.outbox.oldest.pending.seconds", this::oldestPendingSeconds)
        .description("Idade do evento PENDING mais antigo")
        .register(registry);
  }

  /**
   * Reservas: um unico counter com tag de resultado. Os sufixos
   * "created"/"total" sao reservados no cliente Prometheus do Micrometer
   * (que deriva as series *_total e *_created do nome da familia) e seriam
   * descartados, colapsando os counters no mesmo nome.
   */
  public void appointmentCreated() {
    registry.counter("barberflow.appointments", "outcome", "created").increment();
  }

  public void appointmentCancelled() {
    registry.counter("barberflow.appointments", "outcome", "cancelled").increment();
  }

  public void appointmentConflict() {
    registry.counter("barberflow.appointments", "outcome", "conflict").increment();
  }

  public void waitlistOffered() {
    registry.counter("barberflow.waitlist.offers").increment();
  }

  public void waitlistAccepted() {
    registry.counter("barberflow.waitlist.offers.accepted").increment();
  }

  public void waitlistExpired() {
    registry.counter("barberflow.waitlist.offers.expired").increment();
  }

  public void deliveryFailed() {
    registry.counter("barberflow.whatsapp.delivery.failed").increment();
  }

  private double oldestPendingSeconds() {
    Instant oldest = outbox.oldestPendingCreatedAt();
    return oldest == null ? 0.0 : Duration.between(oldest, Instant.now()).getSeconds();
  }
}
