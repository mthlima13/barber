package com.barberflow.notification.application;

import com.barberflow.notification.infrastructure.WhatsAppProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client de log usado quando o WhatsApp esta desabilitado
 * ({@code barberflow.whatsapp.enabled=false}, padrao local/testes): o worker
 * marca o evento como SENT sem trafego externo (spec, secao 12: a entrega
 * real exige numero, credenciais e templates aprovados pela Meta).
 *
 * <p>Loga apenas o tamanho da mensagem — NUNCA telefone ou conteudo
 * sensivel (spec, linha 222).</p>
 */
public class LoggingWhatsAppClient implements WhatsAppClient {

  private static final Logger log = LoggerFactory.getLogger(LoggingWhatsAppClient.class);

  private final WhatsAppProperties properties;

  public LoggingWhatsAppClient(WhatsAppProperties properties) {
    this.properties = properties;
  }

  @Override
  public String sendText(String phoneE164, String message) {
    log.info(
        "WhatsApp desabilitado (enabled={}): mensagem suprimida ({} caracteres)",
        properties.enabled(),
        message.length());
    return "disabled-" + UUID.randomUUID();
  }
}
