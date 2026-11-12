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

  /** Consentimento LGPD da politica de privacidade (spec, linha 223). */
  @Column(name = "consent_at")
  private Instant consentAt;

  /** Origem do consentimento (web-booking, web-waitlist...). */
  @Column(name = "consent_source")
  private String consentSource;

  /** Versao da politica de privacidade aceita. */
  @Column(name = "privacy_policy_version")
  private String privacyPolicyVersion;

  /** Exclusao do titular: dados pessoais substituidos por anonimos. */
  @Column(name = "anonymized_at")
  private Instant anonymizedAt;

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

  /** Consentimento explicito na coleta (origem + versao da politica). */
  public void registrarConsentimento(String policyVersion, String source) {
    this.consentAt = Instant.now();
    this.consentSource = source;
    this.privacyPolicyVersion = policyVersion;
  }

  /**
   * Canal de exclusao do titular (LGPD): nome e telefone deixam de ser
   * pessoalmente identificaveis; consentimento removido. O telefone
   * anonimo mantem a unicidade por tenant ({@code anon-<hash>@invalid.local}).
   */
  public void anonimizar(String anonymizedName, String anonymizedPhone) {
    this.name = anonymizedName;
    this.phoneE164 = anonymizedPhone;
    this.consentAt = null;
    this.consentSource = null;
    this.privacyPolicyVersion = null;
    this.anonymizedAt = Instant.now();
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

  public Instant getConsentAt() {
    return consentAt;
  }

  public String getConsentSource() {
    return consentSource;
  }

  public String getPrivacyPolicyVersion() {
    return privacyPolicyVersion;
  }

  public Instant getAnonymizedAt() {
    return anonymizedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
