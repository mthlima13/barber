package com.barberflow.appointment.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** Mapeamento da tabela {@code customers} (migracao V4). */
@Entity
@Table(
    name = "customers",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_customers_tenant_phone", columnNames = {"tenant_id", "phone_e164"}))
public class CustomerJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String name;

  @Column(name = "phone_e164", nullable = false)
  private String phoneE164;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CustomerJpaEntity() {}

  private CustomerJpaEntity(UUID tenantId, String name, String phoneE164) {
    this.tenantId = tenantId;
    this.name = name;
    this.phoneE164 = phoneE164;
  }

  public static CustomerJpaEntity novo(UUID tenantId, String name, String phoneE164) {
    return new CustomerJpaEntity(tenantId, name, phoneE164);
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

  public String getName() {
    return name;
  }

  public String getPhoneE164() {
    return phoneE164;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
