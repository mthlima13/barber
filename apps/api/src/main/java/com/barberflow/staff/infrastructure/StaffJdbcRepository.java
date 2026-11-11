package com.barberflow.staff.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acesso JDBC a tabelas sem entidade JPA no modulo staff.
 *
 * <p>{@code barber_services} tem chave composta (tenant, barbeiro, servico) e
 * FKs compostas (migracao V2); o mapeamento JDBC evita o peso de uma entidade
 * com {@code @EmbeddedId} nesta etapa.</p>
 *
 * <p>TODO(auth): validar que o usuario vinculado tem membership ativa no
 * tenant (spec, secao 2) quando o modulo auth estiver disponivel.</p>
 */
@Repository
public class StaffJdbcRepository {

  private final JdbcTemplate jdbc;

  public StaffJdbcRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean userExists(UUID userId) {
    Integer count =
        jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, userId);
    return count != null && count > 0;
  }

  public List<UUID> findEnabledServiceIds(UUID tenantId, UUID barberId) {
    return jdbc.queryForList(
        "SELECT service_id FROM barber_services WHERE tenant_id = ? AND barber_id = ?"
            + " ORDER BY created_at, service_id",
        UUID.class,
        tenantId,
        barberId);
  }

  /** Substitui a lista de servicos habilitados (delete + insert, em transacao). */
  public void replaceEnabledServices(UUID tenantId, UUID barberId, List<UUID> serviceIds) {
    jdbc.update(
        "DELETE FROM barber_services WHERE tenant_id = ? AND barber_id = ?", tenantId, barberId);
    for (UUID serviceId : serviceIds) {
      jdbc.update(
          "INSERT INTO barber_services (tenant_id, barber_id, service_id) VALUES (?, ?, ?)",
          tenantId,
          barberId,
          serviceId);
    }
  }
}
