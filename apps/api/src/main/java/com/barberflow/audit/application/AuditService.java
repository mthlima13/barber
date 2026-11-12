package com.barberflow.audit.application;

import com.barberflow.audit.infrastructure.AuditEventJpaEntity;
import com.barberflow.audit.infrastructure.AuditEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de acoes do gestor na tabela {@code audit_events} (spec, secao 6),
 * na mesma transacao da operacao auditada. Sem dados sensentes: ids, acao e
 * momento.
 */
@Service
public class AuditService {

  /** Tipo de entidade de reserva (entity_type). */
  public static final String ENTITY_APPOINTMENT = "APPOINTMENT";

  private final AuditEventRepository audits;

  public AuditService(AuditEventRepository audits) {
    this.audits = audits;
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public void record(
      UUID tenantId, UUID actorId, String action, String entityType, UUID entityId) {
    audits.save(AuditEventJpaEntity.novo(tenantId, actorId, action, entityType, entityId));
  }
}
