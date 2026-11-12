package com.barberflow.privacy;

import com.barberflow.auth.domain.AuthPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Direitos do titular (LGPD, spec, linha 223) autenticados pelo token de
 * gerenciamento da reserva (spec, secao 9.1):
 *
 * <ul>
 *   <li>GET — relatorio de acesso: dados mantidos, consentimento e
 *       inscricoes/reservas ligadas ao titular.</li>
 *   <li>DELETE — canal de exclusao: anonimiza nome e telefone (uso unico
 *       e irreversivel; reservas permanecem como registro de negocio).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/customer/appointments")
public class PrivacyController {

  private final PrivacyService privacy;

  public PrivacyController(PrivacyService privacy) {
    this.privacy = privacy;
  }

  @GetMapping("/{id}/privacy")
  public PrivacyReportResponse report(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
    return privacy.report(principal, id);
  }

  @DeleteMapping("/{id}/privacy")
  public PrivacyReportResponse anonymize(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
    return privacy.anonymize(principal, id);
  }
}
