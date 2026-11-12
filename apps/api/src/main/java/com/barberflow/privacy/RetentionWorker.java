package com.barberflow.privacy;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Politica de retencao (LGPD, spec, linha 223): clientes sem nenhuma
 * reserva apos o prazo configurado sao anonimizados automaticamente
 * (minimizacao de dados pessoais). Desabilitado por padrao — o prazo
 * final fica sob orientacao juridica (spec, linha 223).
 */
@Component
public class RetentionWorker {

  private static final Logger log = LoggerFactory.getLogger(RetentionWorker.class);

  private final PrivacyProperties properties;
  private final PrivacyService privacy;

  public RetentionWorker(PrivacyProperties properties, PrivacyService privacy) {
    this.properties = properties;
    this.privacy = privacy;
  }

  /** Executa diariamente quando a retencao esta habilitada. */
  @Scheduled(cron = "${barberflow.privacy.retention-cron:0 0 3 * * *}")
  public void run() {
    if (!properties.retentionEnabled()) {
      return;
    }
    Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(30L * properties.retentionMonths()));
    int anonymized = privacy.anonymizeStale(cutoff);
    if (anonymized > 0) {
      log.info("Retencao LGPD: {} clientes anonimizados (sem atividade desde {})", anonymized, cutoff);
    }
  }
}
