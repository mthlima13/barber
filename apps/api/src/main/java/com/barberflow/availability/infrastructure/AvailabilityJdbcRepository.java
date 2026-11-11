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
   * Reservas ativas (PENDING/CONFIRMED) que intersectam o dia, como intervalos
   * UTC {@code [start, end)} (spec, secao 7.5).
   */
  public List<long[]> reservedIntervals(
      UUID tenantId, UUID barberId, Instant dayStart, Instant dayEnd) {
    return jdbc.query(
        "SELECT starts_at, ends_at FROM appointments"
            + " WHERE tenant_id = ? AND barber_id = ?"
            + " AND status IN ('PENDING', 'CONFIRMED')"
            + " AND ends_at > ? AND starts_at < ?"
            + " ORDER BY starts_at",
        (rs, i) ->
            new long[] {
              rs.getTimestamp(1).getTime(), rs.getTimestamp(2).getTime()
            },
        tenantId,
        barberId,
        java.sql.Timestamp.from(dayStart),
        java.sql.Timestamp.from(dayEnd));
  }
}
