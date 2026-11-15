package com.barberflow.notification.application;

import com.barberflow.appointment.infrastructure.AppointmentJpaEntity;
import com.barberflow.appointment.infrastructure.AppointmentRepository;
import com.barberflow.appointment.infrastructure.CustomerJpaEntity;
import com.barberflow.appointment.infrastructure.CustomerRepository;
import com.barberflow.notification.infrastructure.OutboxEventJpaEntity;
import com.barberflow.notification.infrastructure.OutboxRepository;
import com.barberflow.notification.infrastructure.WhatsAppProperties;
import com.barberflow.tenant.infrastructure.TenantJpaEntity;
import com.barberflow.tenant.infrastructure.TenantRepository;
import com.barberflow.waitlist.infrastructure.WaitlistEntryJpaEntity;
import com.barberflow.waitlist.infrastructure.WaitlistEntryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Worker de entrega da outbox (spec, linha 231): busca eventos PENDING
 * vencidos, renderiza a mensagem e envia via Cloud API. A transicao de
 * status acontece em transacao PROPRIA (REQUIRES_NEW) para sobreviver a
 * falhas e ser visivel imediatamente; retries com backoff exponencial
 * (spec, linha 233) ate o limite, quando o evento vai para FAILED.
 */
@Component
public class OutboxWorker {
  private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

  /** Maximo de tentativas por evento antes de FAILED (fila de analise). */
  static final int MAX_ATTEMPTS = 5;

  private final OutboxRepository outbox;
  private final CustomerRepository customers;
  private final TenantRepository tenants;
  private final AppointmentRepository appointments;
  private final WaitlistEntryRepository waitlistEntries;
  private final WhatsAppClient whatsApp;
  private final OutboxDeliveryService delivery;
  private final com.barberflow.metrics.BusinessMetrics metrics;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Object lock = new Object();
  private final long firstBackoffSeconds;
  private final WhatsAppProperties whatsAppProperties;

  public OutboxWorker(
      OutboxRepository outbox,
      CustomerRepository customers,
      TenantRepository tenants,
      AppointmentRepository appointments,
      WaitlistEntryRepository waitlistEntries,
      WhatsAppClient whatsApp,
      OutboxDeliveryService delivery,
      com.barberflow.metrics.BusinessMetrics metrics,
      WhatsAppProperties whatsAppProperties,
      @Value("${barberflow.whatsapp.first-backoff-seconds:30}") long firstBackoffSeconds) {
    this.outbox = outbox;
    this.customers = customers;
    this.tenants = tenants;
    this.appointments = appointments;
    this.waitlistEntries = waitlistEntries;
    this.whatsApp = whatsApp;
    this.delivery = delivery;
    this.metrics = metrics;
    this.whatsAppProperties = whatsAppProperties;
    this.firstBackoffSeconds = firstBackoffSeconds;
  }

  /** Polling periodico (intervalo configuravel; testes usam intervalo alto). */
  @Scheduled(fixedDelayString = "${barberflow.whatsapp.poll-interval-millis:15000}")
  public void scheduledRun() {
    runOnce();
  }

  /** Executa um ciclo completo (uma passada no lote). Thread-safe. */
  public void runOnce() {
    synchronized (lock) {
      var due =
          outbox.findDue(
              "PENDING", Instant.now(), PageRequest.of(0, 20));
      for (OutboxEventJpaEntity event : due) {
        deliver(event);
      }
      if (!due.isEmpty()) {
        log.debug("Worker processou {} eventos da outbox", due.size());
      }
    }
  }

  private void deliver(OutboxEventJpaEntity event) {
    if (event.getAttemptCount() >= MAX_ATTEMPTS) {
      metrics.deliveryFailed();
      delivery.markFailed(event.getId(), "Esgotadas " + MAX_ATTEMPTS + " tentativas");
      return;
    }
    try {
      // Supressao: o evento pode ter perdido a razao de existir entre o
      // agendamento e a entrega (reserva cancelada, inscricao fora da fila)
      String motivoSupressao = supressao(event);
      if (motivoSupressao != null) {
        delivery.markSkipped(event.getId(), motivoSupressao);
        return;
      }
      String phone = resolvePhone(event);
      String message = renderMessage(event, phone);
      String messageId;
      if (whatsAppProperties.templateLanguage() != null
          && !whatsAppProperties.templateLanguage().isBlank()) {
        // Mensagem iniciada pela barbearia fora da janela de 24h: so
        // TEMPLATE passa (corpo {{1}} com o texto renderizado; nomes dos
        // templates aprovados na Meta derivam do tipo do evento)
        messageId =
            whatsApp.sendTemplate(
                phone,
                event.getEventType().toLowerCase(Locale.ROOT),
                whatsAppProperties.templateLanguage(),
                message);
      } else {
        messageId = whatsApp.sendText(phone, message);
      }
      delivery.markSent(event.getId(), messageId);
    } catch (Exception e) {
      // Falha transitoria -> backoff exponencial; permanente/esgotado -> FAILED
      String cause = shorten(e.getMessage() == null ? e.toString() : e.getMessage());
      if (event.getAttemptCount() + 1 >= MAX_ATTEMPTS) {
        metrics.deliveryFailed();
        delivery.markFailed(event.getId(), cause);
      } else {
        long backoffSeconds = Duration.ofSeconds(firstBackoffSeconds).multipliedBy(1L << event.getAttemptCount()).toSeconds();
        delivery.markRetry(event.getId(), cause, Instant.now().plusSeconds(backoffSeconds));
      }
    }
  }

  /**
   * Motivo de supressao do evento (null = enviar): lembrete de reserva que
   * o cliente cancelou entre o agendamento e a entrega; oferta de slot
   * para inscricao que nao esta mais aguardando (aceitou outra oferta,
   * expirou ou saiu da fila). Erros de lookup NAO suprimem: podem ser
   * transitorios e seguem o ciclo de retry/FAILED.
   */
  private String supressao(OutboxEventJpaEntity event) {
    Map<String, Object> payload = parsePayload(event);
    Object appointmentId = payload.get("appointmentId");
    if (OutboxEvents.APPOINTMENT_REMINDER.equals(event.getEventType())
        && appointmentId != null) {
      return appointments
          .findById(UUID.fromString(appointmentId.toString()))
          .filter(a -> "CANCELLED".equals(a.getStatus()))
          .map(a -> "Reserva cancelada antes da entrega; lembrete suprimido")
          .orElse(null);
    }
    Object entryId = payload.get("entryId");
    if (OutboxEvents.WAITLIST_SLOT_AVAILABLE.equals(event.getEventType())
        && entryId != null) {
      return waitlistEntries
          .findById(UUID.fromString(entryId.toString()))
          .filter(entry -> !"WAITING".equals(entry.getStatus())
              && !"OFFERED".equals(entry.getStatus()))
          .map(entry -> "Inscricao fora da fila (" + entry.getStatus()
              + "); oferta suprimida")
          .orElse(null);
    }
    return null;
  }

  /**
   * Telefone do destinatario: eventos de gestor (audience=manager) vão para
   * o manager_phone do tenant; os demais, para o cliente do payload (spec 9.1).
   */
  private String resolvePhone(OutboxEventJpaEntity event) {
    Map<String, Object> payload = parsePayload(event);
    if ("manager".equals(payload.get("audience"))) {
      TenantJpaEntity tenant =
          tenants
              .findById(event.getTenantId())
              .orElseThrow(
                  () -> new IllegalStateException("Tenant do evento nao existe"));
      String phone = tenant.getManagerPhone();
      if (phone == null || phone.isBlank()) {
        throw new IllegalStateException("Gestor sem telefone configurado");
      }
      return phone;
    }
    Object customerId = payload.get("customerId");
    if (customerId == null) {
      throw new IllegalStateException("Evento sem customerId no payload");
    }
    CustomerJpaEntity customer =
        customers
            .findById(UUID.fromString(customerId.toString()))
            .orElseThrow(
                () -> new IllegalStateException("Cliente nao encontrado: " + customerId));
    return customer.getPhoneE164();
  }

  private String renderMessage(OutboxEventJpaEntity event, String phone) {
    Map<String, Object> payload = parsePayload(event);
    var tenant =
        tenants
            .findById(event.getTenantId())
            .orElseThrow(() -> new IllegalStateException("Tenant do evento nao existe"));
    Object customerId = payload.get("customerId");
    String name = "";
    if (customerId != null) {
      name =
          customers
              .findById(UUID.fromString(customerId.toString()))
              .map(CustomerJpaEntity::getName)
              .orElse("cliente");
    }
    String message = MessageTemplates.render(event.getEventType(), payload, name, java.time.ZoneId.of(tenant.getTimezone()));
    if (message == null) {
      throw new IllegalStateException("Evento sem template: " + event.getEventType());
    }
    // phone usado apenas para envio; nunca entra no conteudo (spec, linha 222)
    return message;
  }

  private Map<String, Object> parsePayload(OutboxEventJpaEntity event) {
    try {
      return mapper.readValue(event.getPayloadJson(), Map.class);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Payload invalido do evento " + event.getId(), e);
    }
  }

  private static String shorten(String text) {
    return text.length() > 250 ? text.substring(0, 250) : text;
  }
}
