package com.barberflow.audit.infrastructure;

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
 * Mapeamento da tabela {@code audit_events} (migracao V6).
 *
 * <p>Auditoria de acoes do gestor (spec, secao 6): apenas ids, acao e
 * momento — sem dados sensentes desnecessarios. Somente leitura apos o
 * INSERT (linhas imutaveis).</p>
 */
@Entity
@Table(name = "audit_events")
public class AuditEventJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  /** Usuario da equipe responsavel (nulo para acoes do sistema). */
  @Column(name = "actor_id", updatable = false)
  private UUID actorId;

  /** Ex.: APPOINTMENT_CONFIRMED, APPOINTMENT_CANCELLED, APPOINTMENT_NO_SHOW. */
  @Column(nullable = false, updatable = false)
  private String action;

  @Column(name = "entity_type", nullable = false, updatable = false)
  private String entityType;

  @Column(name = "entity_id", updatable = false)
  private UUID entityId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AuditEventJpaEntity() {}

  private AuditEventJpaEntity(
      UUID tenantId, UUID actorId, String action, String entityType, UUID entityId) {
    this.tenantId = tenantId;
    this.actorId = actorId;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
  }

  public static AuditEventJpaEntity novo(
      UUID tenantId, UUID actorId, String action, String entityType, UUID entityId) {
    return new AuditEventJpaEntity(tenantId, actorId, action, entityType, entityId);
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

  public UUID getActorId() {
    return actorId;
  }

  public String getAction() {
    return action;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
