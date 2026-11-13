package com.barberflow.staff.application;

import com.barberflow.auth.infrastructure.AuthJdbcRepository;
import com.barberflow.auth.infrastructure.UserJpaEntity;
import com.barberflow.auth.infrastructure.UserRepository;
import com.barberflow.staff.api.BarberResponse;
import com.barberflow.staff.infrastructure.BarberJpaEntity;
import com.barberflow.staff.infrastructure.BarberRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Credenciais de acesso do profissional (spec, linha 218: autorizacao por
 * papel E por recurso): o gestor cria a conta, o usuario recebe papel
 * BARBER na membership e o perfil fica vinculado via {@code barbers.user_id}.
 *
 * <p>O papel BARBER autoriza APENAS a propria agenda (spec, linha 361):
 * ele nunca acessa CRUD da equipe/servicos nem a agenda de outros.</p>
 */
@Service
public class BarberCredentialsService {

  private final BarberRepository barbers;
  private final UserRepository users;
  private final AuthJdbcRepository authJdbc;
  private final PasswordEncoder passwordEncoder;

  public BarberCredentialsService(
      BarberRepository barbers,
      UserRepository users,
      AuthJdbcRepository authJdbc,
      PasswordEncoder passwordEncoder) {
    this.barbers = barbers;
    this.users = users;
    this.authJdbc = authJdbc;
    this.passwordEncoder = passwordEncoder;
  }

  /** Cria a conta BARBER e vincula ao perfil; conta existente -> 409. */
  @Transactional
  public BarberResponse.Credentials createCredentials(
      UUID tenantId, UUID barberId, String email, String password) {
    BarberJpaEntity barber =
        barbers
            .findByIdAndTenantId(barberId, tenantId)
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Profissional nao encontrado para esta barbearia"));

    if (barber.getUserId() != null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Profissional ja possui conta de acesso");
    }
    if (email == null || email.isBlank() || !email.contains("@")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email invalido");
    }
    if (password == null || password.length() < 8) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Senha precisa ter pelo menos 8 caracteres");
    }
    if (users.existsByEmailIgnoreCase(email)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado");
    }

    // Flush necessario: o INSERT da membership via JDBC roda fora do
    // Hibernate e precisa do usuario ja gravado (FK tenant_memberships).
    UserJpaEntity user =
        users.saveAndFlush(
            UserJpaEntity.novo(barber.getDisplayName(), email, passwordEncoder.encode(password)));
    authJdbc.insertMembership(tenantId, user.getId(), "BARBER");

    barber.vincularUsuario(user.getId());
    barbers.save(barber);

    return new BarberResponse.Credentials(barber.getId(), user.getId(), user.getEmail(), "BARBER");
  }
}
