package com.barberflow.notification.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento da tabela {@code notification_outbox} (migracao V6).
 *
 * <p>Outbox (spec, secao 4): o evento de notificacao e gravado na MESMA
 * transacao da operacao de negocio — falha no envio posterior nunca perde o
 * evento nem desfaz a reserva (spec, linha 366). O worker de entrega entra
 * na Fase 4; ate la os eventos ficam PENDING para observabilidade.</p>
 */
@Entity
@Table(name = "notification_outbox")
public class OutboxEventJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  /** APPOINTMENT_CREATED, APPOINTMENT_CANCELLED, APPOINTMENT_RESCHEDULED... */
  @Column(name = "event_type", nullable = false, updatable = false)
  private String eventType;

  @Column(name = "payload_json", nullable = false, updatable = false)
  private String payloadJson;

  /** PENDING, SENT ou FAILED (CHECK na V6); worker da Fase 4 transita. */
  @Column(nullable = false)
  private String status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  /** Proxima tentativa (backoff do worker); nulo ate a primeira falha. */
  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "idempotency_key", updatable = false)
  private String idempotencyKey;

  /** ID da mensagem aceita pela Cloud API (webhook correlaciona). */
  @Column(name = "message_id")
  private String messageId;

  /** Ultimo erro de entrega (diagnostico no dashboard; sem segredos). */
  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Versionamento otimista para o worker (transicao PENDING -> SENT). */
  @Version
  private Long version;

  protected OutboxEventJpaEntity() {}

  private OutboxEventJpaEntity(
      UUID tenantId,
      String eventType,
      String payloadJson,
      String idempotencyKey) {
    this.tenantId = tenantId;
    this.eventType = eventType;
    this.payloadJson = payloadJson;
    this.idempotencyKey = idempotencyKey;
    this.status = "PENDING";
  }

  public static OutboxEventJpaEntity novo(
      UUID tenantId, String eventType, String payloadJson, String idempotencyKey) {
    return new OutboxEventJpaEntity(tenantId, eventType, payloadJson, idempotencyKey);
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public String getStatus() {
    return status;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getMessageId() {
    return messageId;
  }

  public String getLastError() {
    return lastError;
  }

  public Long getVersion() {
    return version;
  }

  /** Entrega confirmada pela Cloud API; tenta uma unica vez (uso unico). */
  public void marcarEnviado(String messageId) {
    this.status = "SENT";
    this.attemptCount++;
    this.messageId = messageId;
    this.nextAttemptAt = null;
  }

  /** Falha transitoria: tenta de novo apos o backoff informado. */
  public void registrarFalha(String cause, Instant nextAttemptAt) {
    this.status = "PENDING";
    this.attemptCount++;
    this.lastError = cause;
    this.nextAttemptAt = nextAttemptAt;
  }

  /** Erro permanente/esgotado: vai para analise (spec, linha 233). */
  public void marcarFalhaFinal(String cause) {
    this.status = "FAILED";
    this.attemptCount++;
    this.lastError = cause;
    this.nextAttemptAt = null;
  }
}
