package com.barberflow.notification.application;

import com.barberflow.notification.infrastructure.OutboxEventJpaEntity;
import com.barberflow.notification.infrastructure.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transicoes de entrega do worker em transacao PROPRIA (REQUIRES_NEW):
 * o estado de cada evento persiste independentemente do ciclo do worker e
 * fica visivel no dashboard imediatamente. Recarrega a entidade pelo id
 * para respeitar o versionamento otimista (@Version).
 */
@Service
public class OutboxDeliveryService {

  private final OutboxRepository outbox;

  public OutboxDeliveryService(OutboxRepository outbox) {
    this.outbox = outbox;
  }

  /** Aceita pela Cloud API: SENT com o id da mensagem (uso unico). */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markSent(UUID eventId, String messageId) {
    outbox
        .findById(eventId)
        .filter(event -> "PENDING".equals(event.getStatus()))
        .ifPresent(event -> {
          event.marcarEnviado(messageId);
          outbox.save(event);
        });
  }

  /** Falha transitoria: nova tentativa apos o backoff. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markRetry(UUID eventId, String cause, Instant nextAttemptAt) {
    outbox
        .findById(eventId)
        .filter(event -> "PENDING".equals(event.getStatus()))
        .ifPresent(event -> {
          event.registrarFalha(cause, nextAttemptAt);
          outbox.save(event);
        });
  }

  /** Erro permanente ou tentativas esgotadas: FAILED (fila de analise). */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(UUID eventId, String cause) {
    outbox
        .findById(eventId)
        .filter(event -> !"SENT".equals(event.getStatus()))
        .ifPresent(event -> {
          event.marcarFalhaFinal(cause);
          outbox.save(event);
        });
  }
}
