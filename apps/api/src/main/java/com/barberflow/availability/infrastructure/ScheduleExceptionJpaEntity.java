package com.barberflow.availability.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento da tabela {@code schedule_exceptions} (migracao V3).
 *
 * <p>Intervalo semiaberto {@code [start_at, end_at)} (spec, secao 7.5).</p>
 */
@Entity
@Table(name = "schedule_exceptions")
public class ScheduleExceptionJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "barber_id", nullable = false, updatable = false)
  private UUID barberId;

  @Column(name = "start_at", nullable = false)
  private Instant startAt;

  @Column(name = "end_at", nullable = false)
  private Instant endAt;

  /** DAY_OFF ou BLOCK (CHECK na migracao V3). */
  @Column(nullable = false)
  private String kind;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ScheduleExceptionJpaEntity() {}

  private ScheduleExceptionJpaEntity(
      UUID tenantId, UUID barberId, Instant startAt, Instant endAt, String kind) {
    this.tenantId = tenantId;
    this.barberId = barberId;
    this.startAt = startAt;
    this.endAt = endAt;
    this.kind = kind;
  }

  public static ScheduleExceptionJpaEntity novo(
      UUID tenantId, UUID barberId, Instant startAt, Instant endAt, String kind) {
    return new ScheduleExceptionJpaEntity(tenantId, barberId, startAt, endAt, kind);
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

  public UUID getBarberId() {
    return barberId;
  }

  public Instant getStartAt() {
    return startAt;
  }

  public Instant getEndAt() {
    return endAt;
  }

  public String getKind() {
    return kind;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
