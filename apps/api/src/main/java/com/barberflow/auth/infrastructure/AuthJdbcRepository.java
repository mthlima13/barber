package com.barberflow.auth.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acesso JDBC a {@code tenant_memberships} (chave composta tenant+usuario,
 * migracao V2) e consultas de bootstrap.
 */
@Repository
public class AuthJdbcRepository {

  private final JdbcTemplate jdbc;

  public AuthJdbcRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insertMembership(UUID tenantId, UUID userId, String role) {
    jdbc.update(
        "INSERT INTO tenant_memberships (tenant_id, user_id, role) VALUES (?, ?, ?)",
        tenantId,
        userId,
        role);
  }

  /** Papel do usuario no tenant; vazio se nao houver membership ativa. */
  public Optional<String> findRole(UUID tenantId, UUID userId) {
    return jdbc
        .queryForList(
            "SELECT role FROM tenant_memberships"
                + " WHERE tenant_id = ? AND user_id = ? AND active = true",
            String.class,
            tenantId,
            userId)
        .stream()
        .findFirst();
  }

  /** Indica se o tenant ja possui um gestor (bootstrap idempotente). */
  public boolean tenantHasManager(UUID tenantId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM tenant_memberships"
                + " WHERE tenant_id = ? AND role = 'MANAGER' AND active = true",
            Integer.class,
            tenantId);
    return count != null && count > 0;
  }

  /** Primeiro tenant com membership ativa do usuario (uso no login). */
  public Optional<UUID> findActiveTenantIdOfUser(UUID userId) {
    return jdbc
        .queryForList(
            "SELECT tenant_id FROM tenant_memberships"
                + " WHERE user_id = ? AND active = true ORDER BY created_at, tenant_id",
            UUID.class,
            userId)
        .stream()
        .findFirst();
  }
}
