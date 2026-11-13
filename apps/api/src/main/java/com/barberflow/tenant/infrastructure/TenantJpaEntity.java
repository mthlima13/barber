package com.barberflow.tenant.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Mapeamento da tabela {@code tenants} (migracao V1). */
@Entity
@Table(name = "tenants")
public class TenantJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String slug;

  @Column(nullable = false)
  private String name;

  @Column(name = "timezone", nullable = false)
  private String timezone;

  @Column(nullable = false)
  private String status;

  /** WhatsApp do gestor para notificacoes de negocio (nulo = desligado). */
  @Column(name = "manager_phone", length = 20)
  private String managerPhone;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TenantJpaEntity() {}

  private TenantJpaEntity(String slug, String name, String timezone) {
    this.slug = slug;
    this.name = name;
    this.timezone = timezone;
  }

  /** Fabrica usada pelo bootstrap do modulo auth (spec, secao 2). */
  public static TenantJpaEntity novo(String slug, String name, String timezone) {
    return new TenantJpaEntity(slug, name, timezone);
  }

  @PrePersist
  void onCreate() {
    if (status == null) {
      status = "ACTIVE";
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public UUID getId() {
    return id;
  }

  public String getSlug() {
    return slug;
  }

  public String getName() {
    return name;
  }

  public String getTimezone() {
    return timezone;
  }

  public String getStatus() {
    return status;
  }

  public String getManagerPhone() {
    return managerPhone;
  }

  /** WhatsApp do gestor (E.164) ou null para desligar as notificacoes. */
  public void definirTelefoneGerente(String managerPhone) {
    this.managerPhone =
        managerPhone == null || managerPhone.isBlank() ? null : managerPhone;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
