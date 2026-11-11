package com.barberflow.service.infrastructure;

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

/** Mapeamento da tabela {@code services} (migracao V2). */
@Entity
@Table(name = "services")
public class ServiceJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String name;

  @Column
  private String description;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Column(name = "price_cents", nullable = false)
  private long priceCents;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ServiceJpaEntity() {}

  private ServiceJpaEntity(
      UUID tenantId, String name, String description, int durationMinutes, long priceCents) {
    this.tenantId = tenantId;
    this.name = name;
    this.description = description;
    this.durationMinutes = durationMinutes;
    this.priceCents = priceCents;
  }

  public static ServiceJpaEntity novo(
      UUID tenantId, String name, String description, int durationMinutes, long priceCents) {
    return new ServiceJpaEntity(tenantId, name, description, durationMinutes, priceCents);
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

  public void atualizar(String name, String description, Integer durationMinutes, Long priceCents) {
    if (name != null) {
      this.name = name;
    }
    if (description != null) {
      this.description = description;
    }
    if (durationMinutes != null) {
      this.durationMinutes = durationMinutes;
    }
    if (priceCents != null) {
      this.priceCents = priceCents;
    }
  }

  public void desativar() {
    this.active = false;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public long getPriceCents() {
    return priceCents;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
