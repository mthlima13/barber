package com.barberflow.availability.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acesso JDBC ao modulo availability: verificacao de servico habilitado
 * (barber_services, migracao V2) e intervalos reservados (agendamentos,
 * tabela entra na Fase 2).
 */
@Repository
public class AvailabilityJdbcRepository {

  private final JdbcTemplate jdbc;

  public AvailabilityJdbcRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Servico habilitado para o profissional no tenant? */
  public boolean serviceEnabled(UUID tenantId, UUID barberId, UUID serviceId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM barber_services"
                + " WHERE tenant_id = ? AND barber_id = ? AND service_id = ?",
            Integer.class,
            tenantId,
            barberId,
            serviceId);
    return count != null && count > 0;
  }

  /**
   * Reservas ativas que intersectam o dia, como intervalos UTC
   * {@code [start, end)} (spec, secao 7.5).
   *
   * <p>TODO(fase-2): consultar a tabela {@code appointments} quando existir
   * (status PENDING/CONFIRMED).</p>
   */
  public List<long[]> reservedIntervals(
      UUID tenantId, UUID barberId, Instant dayStart, Instant dayEnd) {
    return List.of();
  }
}
