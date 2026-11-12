package com.barberflow.privacy;

import com.barberflow.appointment.infrastructure.AppointmentJpaEntity;
import com.barberflow.appointment.infrastructure.AppointmentRepository;
import com.barberflow.appointment.infrastructure.CustomerJpaEntity;
import com.barberflow.appointment.infrastructure.CustomerRepository;
import com.barberflow.auth.domain.AuthPrincipal;
import com.barberflow.waitlist.infrastructure.WaitlistEntryJpaEntity;
import com.barberflow.waitlist.infrastructure.WaitlistEntryRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Direitos do titular (LGPD, spec, linha 223): acesso e exclusao via canal
 * proprio — o token de gerenciamento da reserva (spec, secao 9.1) autentica
 * o titular sem precisar de conta.
 *
 * <p>Exclusao = ANONIMIZACAO: nome e telefone deixam de identificar a
 * pessoa; reservas e auditoria permanecem como registro de negocio sem
 * dado pessoal (impossibilidade de restauracao do dado original).
 * Inscricoes ativas na lista de espera sao canceladas.</p>
 */
@Service
public class PrivacyService {

  private static final String ANONYMIZED_NAME = "Cliente (anonimizado)";

  private final CustomerRepository customers;
  private final AppointmentRepository appointments;
  private final WaitlistEntryRepository waitlist;

  public PrivacyService(
      CustomerRepository customers,
      AppointmentRepository appointments,
      WaitlistEntryRepository waitlist) {
    this.customers = customers;
    this.appointments = appointments;
    this.waitlist = waitlist;
  }

  /** Dados mantidos sobre o titular (direito de acesso, art. 18 LGPD). */
  @Transactional(readOnly = true)
  public PrivacyReportResponse report(AuthPrincipal principal, UUID appointmentId) {
    requireOwner(principal, appointmentId);
    return toReport(appointmentId, principal.tenantId());
  }

  /** Canal de exclusao (art. 18 LGPD): anonimiza e devolve o relatorio. */
  @Transactional
  public PrivacyReportResponse anonymize(AuthPrincipal principal, UUID appointmentId) {
    requireOwner(principal, appointmentId);
    CustomerJpaEntity customer = customerOf(appointmentId, principal.tenantId());
    if (customer.getAnonymizedAt() == null) {
      // Inscricoes na lista de espera sao coleta com finalidade propria:
      // perdem o sentido sem os dados de contato (canceladas).
      for (WaitlistEntryJpaEntity entry : waitlist.findByCustomerIdOrderByCreatedAtDesc(customer.getId())) {
        if (!"CANCELLED".equals(entry.getStatus()) && !"CONVERTED".equals(entry.getStatus())) {
          entry.cancelar();
        }
      }
      customer.anonimizar(ANONYMIZED_NAME, anonymizedPhone(customer.getId()));
    }
    return toReport(appointmentId, principal.tenantId());
  }

  /** Retencao: anonimiza clientes sem reservas desde o corte; qtd tratada. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int anonymizeStale(Instant cutoff) {
    int count = 0;
    for (CustomerJpaEntity customer : customers.findStale(cutoff)) {
      for (WaitlistEntryJpaEntity entry : waitlist.findByCustomerIdOrderByCreatedAtDesc(customer.getId())) {
        if (!"CANCELLED".equals(entry.getStatus()) && !"CONVERTED".equals(entry.getStatus())) {
          entry.cancelar();
        }
      }
      customer.anonimizar(ANONYMIZED_NAME, anonymizedPhone(customer.getId()));
      count++;
    }
    return count;
  }

  private PrivacyReportResponse toReport(UUID appointmentId, UUID tenantId) {
    AppointmentJpaEntity appointment =
        appointments
            .findByIdAndTenantId(appointmentId, tenantId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada"));
    CustomerJpaEntity customer = customerOf(appointmentId, tenantId);

    List<PrivacyReportResponse.AppointmentSummary> reservationList =
        appointments.findByCustomerIdOrderByStartsAtDesc(customer.getId()).stream()
            .map(
                a ->
                    new PrivacyReportResponse.AppointmentSummary(
                        a.getId(), a.getStartsAt(), a.getEndsAt(), a.getStatus()))
            .toList();

    List<PrivacyReportResponse.WaitlistSummary> waitlistList =
        waitlist.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
            .map(
                e ->
                    new PrivacyReportResponse.WaitlistSummary(
                        e.getId(), e.getDesiredStartAt(), e.getStatus()))
            .toList();

    return new PrivacyReportResponse(
        new PrivacyReportResponse.CustomerInfo(
            customer.getName(),
            customer.getPhoneE164(),
            customer.getConsentAt(),
            customer.getConsentSource(),
            customer.getPrivacyPolicyVersion(),
            customer.getAnonymizedAt()),
        reservationList,
        waitlistList);
  }

  private CustomerJpaEntity customerOf(UUID appointmentId, UUID tenantId) {
    AppointmentJpaEntity appointment =
        appointments
            .findByIdAndTenantId(appointmentId, tenantId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada"));
    return customers
        .findById(appointment.getCustomerId())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente nao encontrado"));
  }

  private void requireOwner(AuthPrincipal principal, UUID appointmentId) {
    if (principal == null || !"CUSTOMER".equals(principal.role())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Token de gerenciamento necessario");
    }
    if (!principal.userId().equals(appointmentId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada");
    }
  }

  /**
   * Telefone anonimo e unico por tenant (unicidade de phone_e164):
   * derivado do id do cliente, nao reconstitui o original.
   */
  private static String anonymizedPhone(UUID customerId) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String hex =
          HexFormat.of()
              .formatHex(digest.digest(customerId.toString().getBytes(StandardCharsets.UTF_8)));
      return "anon-" + hex.substring(0, 16) + "@invalid.local";
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao anonimizar telefone", e);
    }
  }
}
