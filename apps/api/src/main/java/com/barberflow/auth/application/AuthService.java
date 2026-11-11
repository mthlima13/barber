package com.barberflow.auth.application;

import com.barberflow.auth.api.LoginResponse;
import com.barberflow.auth.api.MeResponse;
import com.barberflow.auth.domain.AuthPrincipal;
import com.barberflow.auth.infrastructure.AuthJdbcRepository;
import com.barberflow.auth.infrastructure.JwtService;
import com.barberflow.auth.infrastructure.UserJpaEntity;
import com.barberflow.auth.infrastructure.UserRepository;
import com.barberflow.tenant.infrastructure.TenantJpaEntity;
import com.barberflow.tenant.infrastructure.TenantRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Autenticacao da equipe: login com email/senha e bootstrap do primeiro
 * gestor da barbearia (spec, secoes 2 e 11).
 */
@Service
public class AuthService {

  private final UserRepository users;
  private final AuthJdbcRepository authJdbc;
  private final TenantRepository tenants;
  private final JwtService jwt;
  private final PasswordEncoder passwordEncoder;
  private final String defaultTenantSlug;
  private final String defaultTenantName;
  private final String defaultTenantTimezone;

  public AuthService(
      UserRepository users,
      AuthJdbcRepository authJdbc,
      TenantRepository tenants,
      JwtService jwt,
      PasswordEncoder passwordEncoder,
      @Value("${barberflow.default-tenant-slug}") String defaultTenantSlug,
      @Value("${barberflow.default-tenant-name:Barbearia}") String defaultTenantName,
      @Value("${barberflow.default-tenant-timezone:America/Sao_Paulo}")
          String defaultTenantTimezone) {
    this.users = users;
    this.authJdbc = authJdbc;
    this.tenants = tenants;
    this.jwt = jwt;
    this.passwordEncoder = passwordEncoder;
    this.defaultTenantSlug = defaultTenantSlug;
    this.defaultTenantName = defaultTenantName;
    this.defaultTenantTimezone = defaultTenantTimezone;
  }

  @Transactional(readOnly = true)
  public LoginResponse login(String email, String password) {
    UserJpaEntity user =
        users
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> unauthorized("Credenciais invalidas"));

    if (!user.isActive()
        || !passwordEncoder.matches(password, user.getPasswordHash())) {
      throw unauthorized("Credenciais invalidas");
    }

    // MVP: uma barbearia; pega a primeira membership ativa do usuario.
    UUID tenantId =
        authJdbc.findActiveTenantIdOfUser(user.getId())
            .orElseThrow(() -> unauthorized("Usuario sem acesso a barbearia"));

    String role =
        authJdbc
            .findRole(tenantId, user.getId())
            .orElseThrow(() -> unauthorized("Usuario sem acesso a barbearia"));

    return buildLoginResponse(user, tenantId, role);
  }

  /**
   * Cria o tenant (se inexistente) e o primeiro gestor. Idempotente na pratica:
   * responde 409 se o tenant ja tem gestor ou se o email ja esta em uso.
   */
  @Transactional
  public LoginResponse bootstrap(String name, String email, String password) {
    TenantJpaEntity tenant =
        tenants
            .findBySlug(defaultTenantSlug)
            .orElseGet(
                () ->
                    tenants.saveAndFlush(
                        TenantJpaEntity.novo(
                            defaultTenantSlug, defaultTenantName, defaultTenantTimezone)));

    if (authJdbc.tenantHasManager(tenant.getId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A barbearia ja possui um gestor cadastrado");
    }
    if (users.existsByEmailIgnoreCase(email)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado");
    }

    UserJpaEntity user =
        users.saveAndFlush(
            UserJpaEntity.novo(name, email, passwordEncoder.encode(password)));
    authJdbc.insertMembership(tenant.getId(), user.getId(), "MANAGER");

    return buildLoginResponse(user, tenant.getId(), "MANAGER");
  }

  @Transactional(readOnly = true)
  public MeResponse me(AuthPrincipal principal) {
    UserJpaEntity user =
        users
            .findById(principal.userId())
            .orElseThrow(() -> unauthorized("Sessao invalida"));
    return new MeResponse(
        user.getId(), user.getName(), user.getEmail(), principal.tenantId(), principal.role());
  }

  private LoginResponse buildLoginResponse(UserJpaEntity user, UUID tenantId, String role) {
    AuthPrincipal principal = new AuthPrincipal(user.getId(), tenantId, role);
    return new LoginResponse(
        jwt.issue(principal),
        "Bearer",
        jwt.ttlSeconds(),
        new MeResponse(user.getId(), user.getName(), user.getEmail(), tenantId, role),
        tenantId,
        role);
  }

  private ResponseStatusException unauthorized(String message) {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
  }
}
