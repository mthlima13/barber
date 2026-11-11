package com.barberflow.waitlist.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento da tabela {@code waitlist_entries} (migracao V5).
 *
 * <p>Pedido de alerta por horario especifico (spec, secao 9.2): servico e
 * intervalo desejado congelados na inscricao; FIFO por criacao.</p>
 */
@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntryJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "barber_id", nullable = false, updatable = false)
  private UUID barberId;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "service_id", nullable = false, updatable = false)
  private UUID serviceId;

  @Column(name = "desired_start_at", nullable = false, updatable = false)
  private Instant desiredStartAt;

  @Column(name = "desired_end_at", nullable = false, updatable = false)
  private Instant desiredEndAt;

  /** WAITING, OFFERED, FULFILLED, CANCELLED ou EXPIRED (CHECK na V5). */
  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Prazo da oferta corrente (nulo enquanto WAITING). */
  @Column(name = "expires_at")
  private Instant expiresAt;

  protected WaitlistEntryJpaEntity() {}

  private WaitlistEntryJpaEntity(
      UUID tenantId,
      UUID barberId,
      UUID customerId,
      UUID serviceId,
      Instant desiredStartAt,
      Instant desiredEndAt) {
    this.tenantId = tenantId;
    this.barberId = barberId;
    this.customerId = customerId;
    this.serviceId = serviceId;
    this.desiredStartAt = desiredStartAt;
    this.desiredEndAt = desiredEndAt;
    this.status = "WAITING";
  }

  public static WaitlistEntryJpaEntity novo(
      UUID tenantId,
      UUID barberId,
      UUID customerId,
      UUID serviceId,
      Instant desiredStartAt,
      Instant desiredEndAt) {
    return new WaitlistEntryJpaEntity(
        tenantId, barberId, customerId, serviceId, desiredStartAt, desiredEndAt);
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  /** Oferta corrente aceita pelo cliente do link; prazo congelado na entrada. */
  public void oferecer(Instant expiresAt) {
    this.status = "OFFERED";
    this.expiresAt = expiresAt;
  }

  /** Cliente da frente nao respondeu: volta para a fila. */
  public void voltarParaFila() {
    this.status = "WAITING";
    this.expiresAt = null;
  }

  /** Oferta aceita e transformada em reserva. */
  public void cumprir() {
    this.status = "FULFILLED";
  }

  /** Cliente sai da lista. */
  public void cancelar() {
    this.status = "CANCELLED";
    this.expiresAt = null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getBarberId() {
    return barberId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getServiceId() {
    return serviceId;
  }

  public Instant getDesiredStartAt() {
    return desiredStartAt;
  }

  public Instant getDesiredEndAt() {
    return desiredEndAt;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
