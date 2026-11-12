package com.barberflow.notification.application;

/**
 * Entrega de mensagem de texto via WhatsApp Business Cloud API
 * (spec, secao 12). Implementacao HTTP existe quando
 * {@code barberflow.whatsapp.enabled=true}; caso contrario, o worker usa
 * o client de log (desenvolvimento/testes: eventos vao para SENT).
 */
public interface WhatsAppClient {

  /**
   * Envia texto para {@code phoneE164} e devolve o ID da mensagem aceita
   * pela API (correlacionado pelo webhook de status). Falha transitoria ou
   * permanente -> RuntimeException; quem classifica e o worker.
   */
  String sendText(String phoneE164, String message);
}
