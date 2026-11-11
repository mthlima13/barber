package com.barberflow.waitlist.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento da tabela {@code slot_offers} (migracao V5).
 *
 * <p>Oferta temporaria "uma pessoa por vez" (spec, secao 9.2.4): o token
 * nunca e salvo em claro, apenas o hash SHA-256. Enquanto PENDING e valida,
 * a oferta funciona como hold transacional do intervalo.</p>
 *
 * <p>O id e gerado na aplicacao (o token deriva dele, entao precisa existir
 * antes do INSERT); com {@code @Version} nulo, o Hibernate trata a entidade
 * como nova e faz INSERT mesmo com id pre-assinado.</p>
 */
@Entity
@Table(name = "slot_offers")
public class SlotOfferJpaEntity {

  @Id
  private UUID id;

  /** Versionamento otimista; null = entidade nova (INSERT com id propio). */
  @Version
  private Long version;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "waitlist_entry_id", nullable = false, updatable = false)
  private UUID waitlistEntryId;

  @Column(name = "starts_at", nullable = false, updatable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false, updatable = false)
  private Instant endsAt;

  @Column(name = "token_hash", nullable = false, updatable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** PENDING, ACCEPTED, DECLINED ou EXPIRED (CHECK na V5). */
  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected SlotOfferJpaEntity() {}

  private SlotOfferJpaEntity(
      UUID id,
      UUID tenantId,
      UUID waitlistEntryId,
      Instant startsAt,
      Instant endsAt,
      String tokenHash,
      Instant expiresAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.waitlistEntryId = waitlistEntryId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.status = "PENDING";
  }

  /**
   * Criacao com id gerado na aplicacao: o token da oferta e derivado do id
   * (HMAC com segredo do servidor), entao o id precisa existir antes do
   * INSERT (o hash vai na mesma linha).
   */
  public static SlotOfferJpaEntity novoComId(
      UUID id,
      UUID tenantId,
      UUID waitlistEntryId,
      Instant startsAt,
      Instant endsAt,
      String tokenHash,
      Instant expiresAt) {
    return new SlotOfferJpaEntity(
        id, tenantId, waitlistEntryId, startsAt, endsAt, tokenHash, expiresAt);
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public void aceitar() {
    this.status = "ACCEPTED";
  }

  public void expirar() {
    this.status = "EXPIRED";
  }

  /** Ajuste do prazo (usado em testes e processamentos administrativos). */
  public void definirExpiracao(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public boolean expirada(Instant now) {
    return "EXPIRED".equals(status) || !expiresAt.isAfter(now);
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getWaitlistEntryId() {
    return waitlistEntryId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public String getStatus() {
    return status;
  }
}
