package com.barberflow.notification.application;

/**
 * Entrega de mensagem via WhatsApp Business Cloud API (spec, secao 12).
 * Implementacao HTTP existe quando {@code barberflow.whatsapp.enabled=true};
 * caso contrario, o worker usa o client de log (desenvolvimento/testes:
 * eventos vao para SENT).
 */
public interface WhatsAppClient {

  /**
   * Envia texto livre para {@code phoneE164} — so funciona dentro da
   * janela de atendimento de 24h aberta pelo cliente (fora dela a Meta
   * rejeita com erro 131030). Devolve o ID da mensagem aceita pela API
   * (correlacionado pelo webhook de status). Falha transitoria ou
   * permanente -> RuntimeException; quem classifica e o worker.
   */
  String sendText(String phoneE164, String message);

  /**
   * Envia MENSAGEM TEMPLATE (mensagens iniciadas pela barbearia fora da
   * janela de 24h — caso de todas as notificacoes do fluxo): o template
   * {@code templateName} (ex.: {@code appointment_created}) precisa estar
   * aprovado na conta da Meta com corpo {@code {{1}}} e o texto renderizado
   * vai como unico parametro do corpo. Devolve o ID da mensagem.
   */
  String sendTemplate(String phoneE164, String templateName, String language, String bodyText);
}
