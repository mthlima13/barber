package com.barberflow.appointment.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento da tabela {@code appointments} (migracao V4).
 *
 * <p>Intervalo semiaberto {@code [startsAt, endsAt)}; snapshot de preco e
 * duracao congelados no momento da reserva (spec, secao 6); versionamento
 * otimista via {@code version}.</p>
 */
@Entity
@Table(name = "appointments")
public class AppointmentJpaEntity {

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

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  /** PENDING, CONFIRMED, COMPLETED, CANCELLED ou NO_SHOW (CHECK na V4). */
  @Column(nullable = false)
  private String status;

  @Column(name = "price_cents_snapshot", nullable = false, updatable = false)
  private long priceCentsSnapshot;

  @Column(name = "duration_minutes_snapshot", nullable = false, updatable = false)
  private int durationMinutesSnapshot;

  @Column(name = "idempotency_key", updatable = false)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected AppointmentJpaEntity() {}

  private AppointmentJpaEntity(
      UUID tenantId,
      UUID barberId,
      UUID customerId,
      UUID serviceId,
      Instant startsAt,
      Instant endsAt,
      long priceCentsSnapshot,
      int durationMinutesSnapshot,
      String idempotencyKey) {
    this.tenantId = tenantId;
    this.barberId = barberId;
    this.customerId = customerId;
    this.serviceId = serviceId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.status = "PENDING";
    this.priceCentsSnapshot = priceCentsSnapshot;
    this.durationMinutesSnapshot = durationMinutesSnapshot;
    this.idempotencyKey = idempotencyKey;
  }

  public static AppointmentJpaEntity novo(
      UUID tenantId,
      UUID barberId,
      UUID customerId,
      UUID serviceId,
      Instant startsAt,
      Instant endsAt,
      long priceCentsSnapshot,
      int durationMinutesSnapshot,
      String idempotencyKey) {
    return new AppointmentJpaEntity(
        tenantId,
        barberId,
        customerId,
        serviceId,
        startsAt,
        endsAt,
        priceCentsSnapshot,
        durationMinutesSnapshot,
        idempotencyKey);
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

  public void remarcar(Instant startsAt, Instant endsAt) {
    this.startsAt = startsAt;
    this.endsAt = endsAt;
  }

  public void definirStatus(String status) {
    this.status = status;
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

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public String getStatus() {
    return status;
  }

  public long getPriceCentsSnapshot() {
    return priceCentsSnapshot;
  }

  public int getDurationMinutesSnapshot() {
    return durationMinutesSnapshot;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
