package com.barberflow.waitlist.application;

import com.barberflow.waitlist.infrastructure.SlotOfferJpaEntity;
import com.barberflow.waitlist.infrastructure.SlotOfferRepository;
import com.barberflow.waitlist.infrastructure.WaitlistEntryJpaEntity;
import com.barberflow.waitlist.infrastructure.WaitlistEntryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marcacao de expiracao de oferta em transacao propria (REQUIRES_NEW).
 *
 * <p>Quando o aceite falha (oferta vencida ou slot ocupado por terceiro), o
 * rollback do aceite nao pode desfazer a expiracao: a oferta morre e a
 * entrada volta para a fila (spec, secao 9.2.4) — quem vem depois nao pode
 * ressuscitar um hold vencido nem ver a entrada como OFFERED.</p>
 */
@Service
public class WaitlistExpiryService {

  private final SlotOfferRepository offers;
  private final WaitlistEntryRepository entries;

  public WaitlistExpiryService(SlotOfferRepository offers, WaitlistEntryRepository entries) {
    this.offers = offers;
    this.entries = entries;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void expirarOfertaVoltarFila(UUID offerId, UUID entryId) {
    offers
        .findById(offerId)
        .filter(offer -> "PENDING".equals(offer.getStatus()))
        .ifPresent(SlotOfferJpaEntity::expirar);
    entries
        .findById(entryId)
        .filter(entry -> "OFFERED".equals(entry.getStatus()))
        .ifPresent(WaitlistEntryJpaEntity::voltarParaFila);
  }
}
