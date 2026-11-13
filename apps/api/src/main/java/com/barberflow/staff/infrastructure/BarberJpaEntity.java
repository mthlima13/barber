package com.barberflow.staff.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Mapeamento da tabela {@code barbers} (migracao V2). */
@Entity
@Table(name = "barbers")
public class BarberJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected BarberJpaEntity() {}

  private BarberJpaEntity(UUID tenantId, UUID userId, String displayName) {
    this.tenantId = tenantId;
    this.userId = userId;
    this.displayName = displayName;
  }

  public static BarberJpaEntity novo(UUID tenantId, UUID userId, String displayName) {
    return new BarberJpaEntity(tenantId, userId, displayName);
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public void atualizar(String displayName) {
    if (displayName != null) {
      this.displayName = displayName;
    }
  }

  public void definirAtivo(boolean active) {
    this.active = active;
  }

  /** Vincula a conta de acesso do profissional (papel BARBER). */
  public void vincularUsuario(UUID userId) {
    this.userId = userId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
