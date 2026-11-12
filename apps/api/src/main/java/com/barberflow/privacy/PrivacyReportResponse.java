package com.barberflow.privacy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Relatorio de dados do titular (direito de acesso, art. 18 LGPD) e
 * retorno do canal de exclusao. Sem nome/telefone de terceiros (spec,
 * linha 224): apenas dados do proprio titular.
 */
public record PrivacyReportResponse(
    CustomerInfo customer,
    List<AppointmentSummary> appointments,
    List<WaitlistSummary> waitlistEntries) {

  /** Consentimento registrado na coleta (base legal e origem). */
  public record CustomerInfo(
      String name,
      String phone,
      Instant consentAt,
      String consentSource,
      String privacyPolicyVersion,
      Instant anonymizedAt) {}

  public record AppointmentSummary(
      UUID id, Instant startsAt, Instant endsAt, String status) {}

  public record WaitlistSummary(UUID id, Instant desiredStartAt, String status) {}
}
