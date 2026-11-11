package com.barberflow.tenant.application;

import com.barberflow.tenant.infrastructure.TenantRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolucao do tenant para operacoes protegidas do MVP.
 *
 * <p>TODO(auth): enquanto o modulo de autenticacao nao existe (login da equipe,
 * tokens de gerenciamento), o tenant e resolvido por configuracao
 * ({@code barberflow.default-tenant-slug}). Quando {@code auth} entrar em
 * operacao, o tenant deve vir da sessao/JWT validado — nunca de entrada do
 * cliente (spec, secao 5).</p>
 */
@Service
public class TenantService {

  private final TenantRepository tenants;
  private final String defaultTenantSlug;

  public TenantService(
      TenantRepository tenants,
      @Value("${barberflow.default-tenant-slug}") String defaultTenantSlug) {
    this.tenants = tenants;
    this.defaultTenantSlug = defaultTenantSlug;
  }

  /** ID do tenant padrao configurado; falha com 503 se nao estiver provisionado. */
  public UUID requireDefaultTenantId() {
    return tenants
        .findBySlug(defaultTenantSlug)
        .map(t -> t.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Tenant padrao nao provisionado: '" + defaultTenantSlug + "'"));
  }
}
