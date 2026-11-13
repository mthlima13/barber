package com.barberflow.notification.application;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Mensagens de WhatsApp por tipo de evento (spec, linha 235): conteudo
 * objetivo com data/hora e orientacao. SEM segredos/links de token — o
 * gerenciamento continua pelo link seguro que o cliente ja recebeu
 * (spec, linha 219: nao incluir segredos).
 */
public final class MessageTemplates {

  private static final DateTimeFormatter WHEN =
      DateTimeFormatter.ofPattern("dd/MM 'as' HH:mm", Locale.forLanguageTag("pt-BR"));

  private MessageTemplates() {}

  /** Mensagem pronta para o evento; null se o evento nao tem template. */
  public static String render(
      String eventType, Map<String, Object> payload, String customerName, ZoneId zone) {
    return switch (eventType) {
      case OutboxEvents.APPOINTMENT_CREATED ->
          "Ola, "
              + customerName
              + "! Sua reserva esta marcada para "
              + when(payload, "startsAt", zone)
              + ". Se precisar remarcar ou cancelar, use o link seguro que voce recebeu.";
      case OutboxEvents.APPOINTMENT_CANCELLED ->
          "Ola, "
              + customerName
              + "! Sua reserva de "
              + when(payload, "startsAt", zone)
              + " foi cancelada. Quando quiser, agende um novo horario.";
      case OutboxEvents.APPOINTMENT_RESCHEDULED ->
          "Ola, "
              + customerName
              + "! Sua reserva foi remarcada: agora sera "
              + when(payload, "startsAt", zone)
              + ".";
      case OutboxEvents.WAITLIST_SLOT_AVAILABLE ->
          "Boa noticia, "
              + customerName
              + "! Abriu um horario "
              + when(payload, "startsAt", zone)
              + ". Abra o link seguro da sua inscricao na lista de espera para confirmar"
              + " em ate 5 minutos.";
      case OutboxEvents.APPOINTMENT_REMINDER ->
          "Ola, "
              + customerName
              + "! Lembrete: sua reserva esta marcada para "
              + when(payload, "startsAt", zone)
              + ". Se precisar remarcar ou cancelar, use o link seguro que voce recebeu.";
      case OutboxEvents.APPOINTMENT_EXPIRED ->
          "Ola, "
              + customerName
              + "! Sua reserva de "
              + when(payload, "startsAt", zone)
              + " expirou: estava pendente de confirmacao e foi cancelada automaticamente."
              + " Quando quiser, agende um novo horario.";
      case OutboxEvents.MANAGER_APPOINTMENT_CREATED ->
          "Nova reserva para "
              + customerName
              + ": "
              + when(payload, "startsAt", zone)
              + " (status pendente de confirmacao).";
      case OutboxEvents.MANAGER_WAITLIST_JOINED ->
          customerName
              + " entrou na lista de espera para "
              + when(payload, "startsAt", zone)
              + ".";
      case OutboxEvents.MANAGER_APPOINTMENT_CANCELLED ->
          customerName
              + " cancelou a reserva de "
              + when(payload, "startsAt", zone)
              + ". O horario esta livre de novo.";
      case OutboxEvents.MANAGER_APPOINTMENT_RESCHEDULED ->
          customerName
              + " remarcou a reserva: antes "
              + when(payload, "previousStartsAt", zone)
              + ", agora sera "
              + when(payload, "startsAt", zone)
              + ".";
      default -> null;
    };
  }

  private static String when(Map<String, Object> payload, String field, ZoneId zone) {
    Object value = payload.get(field);
    if (value == null) {
      return "(horario a confirmar)";
    }
    return WHEN.format(Instant.parse(value.toString()).atZone(zone));
  }
}
