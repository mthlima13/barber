package com.barberflow.availability.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Mapeamento da tabela {@code business_hours} (migracao V3).
 *
 * <p>{@code weekday}: 0=domingo ... 6=sabado (DOW do PostgreSQL).</p>
 */
@Entity
@Table(name = "business_hours")
public class BusinessHoursJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "barber_id", nullable = false, updatable = false)
  private UUID barberId;

  @Column(name = "weekday", nullable = false)
  private short weekday;

  @Column(name = "start_local", nullable = false)
  private LocalTime startLocal;

  @Column(name = "end_local", nullable = false)
  private LocalTime endLocal;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected BusinessHoursJpaEntity() {}

  private BusinessHoursJpaEntity(
      UUID tenantId, UUID barberId, short weekday, LocalTime startLocal, LocalTime endLocal) {
    this.tenantId = tenantId;
    this.barberId = barberId;
    this.weekday = weekday;
    this.startLocal = startLocal;
    this.endLocal = endLocal;
  }

  public static BusinessHoursJpaEntity novo(
      UUID tenantId, UUID barberId, int weekday, LocalTime startLocal, LocalTime endLocal) {
    return new BusinessHoursJpaEntity(tenantId, barberId, (short) weekday, startLocal, endLocal);
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

  public int getWeekday() {
    return weekday;
  }

  public LocalTime getStartLocal() {
    return startLocal;
  }

  public LocalTime getEndLocal() {
    return endLocal;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
