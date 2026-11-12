package com.barberflow.notification.application;

import com.barberflow.notification.infrastructure.OutboxEventJpaEntity;
import com.barberflow.notification.infrastructure.OutboxRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Gravacao de eventos de notificacao na outbox (spec, secoes 9.1 e 9.2):
 * chamado DENTRO da transacao de negocio — o commit da operacao e do evento
 * e atomico. Envio/entrega sao do worker (Fase 4); falha posterior nunca
 * perde o evento nem desfaz a reserva (spec, linha 366).
 */
@Service
public class OutboxService {

  private final OutboxRepository outbox;
  private final ObjectMapper mapper = new ObjectMapper();

  public OutboxService(OutboxRepository outbox) {
    this.outbox = outbox;
  }

  /**
   * Registra evento PENDING na transacao corrente. Com idempotencyKey, um
   * evento repetido nao se duplica (unique na V6; replay idempotente nao
   * gera segunda notificacao).
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public void append(UUID tenantId, String eventType, String idempotencyKey, Object payload) {
    if (idempotencyKey != null
        && outbox.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).isPresent()) {
      return;
    }
    try {
      outbox.save(
          OutboxEventJpaEntity.novo(
              tenantId, eventType, mapper.writeValueAsString(payload), idempotencyKey));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      // Serializacao de payload local: nunca deve acontecer; propaga para
      // nao commitar a operacao de negocio sem o evento (espec 9.1.5).
      throw new IllegalStateException("Falha ao serializar payload do evento", e);
    }
  }
}
