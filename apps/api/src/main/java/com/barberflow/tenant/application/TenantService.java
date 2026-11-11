package com.barberflow.tenant.application;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolucao do tenant para operacoes protegidas.
 *
 * <p>O tenant vem SEMPRE da sessao autenticada (claims do token JWT) — nunca
 * de entrada do cliente (spec, secao 5). Chamadas sem sessao respondem 401.</p>
 */
@Service
public class TenantService {

  /** Tenant da sessao atual (claim do token); 401 se nao autenticado. */
  public UUID requireCurrentTenantId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal()
            instanceof com.barberflow.auth.domain.AuthPrincipal principal) {
      return principal.tenantId();
    }
    throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED, "Sessao sem tenant: faca login na barbearia");
  }
}
