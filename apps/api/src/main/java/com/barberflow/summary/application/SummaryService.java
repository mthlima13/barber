package com.barberflow.summary.application;

import com.barberflow.appointment.infrastructure.AppointmentJpaEntity;
import com.barberflow.appointment.infrastructure.AppointmentRepository;
import com.barberflow.notification.infrastructure.OutboxRepository;
import com.barberflow.summary.api.SummaryResponse;
import com.barberflow.summary.api.SummaryResponse.AppointmentsSummary;
import com.barberflow.summary.api.SummaryResponse.OutboxSummary;
import com.barberflow.summary.api.SummaryResponse.WaitlistSummary;
import com.barberflow.tenant.infrastructure.TenantJpaEntity;
import com.barberflow.tenant.infrastructure.TenantRepository;
import com.barberflow.waitlist.infrastructure.WaitlistEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Agregacoes do painel (cartoes resumo): uma unica chamada resolve
 * reservas do dia, funil da lista de espera e fila de entrega. As
 * contagens de fila/outbox sao do tenant inteiro (acumulado); as de
 * reservas, do dia informado (ou hoje no fuso da barbearia).
 */
@Service
public class SummaryService {

  private static final List<String> DAY_STATUSES =
      List.of("PENDING", "CONFIRMED", "COMPLETED", "NO_SHOW", "CANCELLED");

  private final AppointmentRepository appointments;
  private final TenantRepository tenants;
  private final WaitlistEntryRepository waitlistEntries;
  private final OutboxRepository outbox;

  public SummaryService(
      AppointmentRepository appointments,
      TenantRepository tenants,
      WaitlistEntryRepository waitlistEntries,
      OutboxRepository outbox) {
    this.appointments = appointments;
    this.tenants = tenants;
    this.waitlistEntries = waitlistEntries;
    this.outbox = outbox;
  }

  @Transactional(readOnly = true)
  public SummaryResponse summary(UUID tenantId, LocalDate date) {
    TenantJpaEntity tenant =
        tenants
            .findById(tenantId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Barbearia nao encontrada"));
    ZoneId zone = ZoneId.of(tenant.getTimezone());
    LocalDate effective = date != null ? date : LocalDate.now(zone);
    Instant dayStart = effective.atStartOfDay(zone).toInstant();
    Instant dayEnd = effective.plusDays(1).atStartOfDay(zone).toInstant();

    Map<String, List<AppointmentJpaEntity>> byStatus =
        appointments
            .findByTenantIdAndStatusInAndStartsAtBetweenOrderByStartsAtAsc(
                tenantId, DAY_STATUSES, dayStart, dayEnd)
            .stream()
            .collect(Collectors.groupingBy(AppointmentJpaEntity::getStatus));

    ToLongFunction<String> count =
        status -> byStatus.getOrDefault(status, List.of()).size();
    // Receita esperada: o que o dia pode render (pendente+confirmada+
    // concluida); NO_SHOW e CANCELLED nao geram receita
    long expectedRevenue =
        java.util.stream.Stream.of("PENDING", "CONFIRMED", "COMPLETED")
            .mapToLong(status -> count.applyAsLong(status) > 0
                ? byStatus.get(status).stream()
                    .mapToLong(AppointmentJpaEntity::getPriceCentsSnapshot)
                    .sum()
                : 0)
            .sum();
    long total = DAY_STATUSES.stream().mapToLong(count::applyAsLong).sum();

    return new SummaryResponse(
        effective.toString(),
        new AppointmentsSummary(
            total,
            count.applyAsLong("PENDING"),
            count.applyAsLong("CONFIRMED"),
            count.applyAsLong("COMPLETED"),
            count.applyAsLong("NO_SHOW"),
            count.applyAsLong("CANCELLED"),
            expectedRevenue),
        new WaitlistSummary(
            waitlistEntries.countByTenantIdAndStatus(tenantId, "WAITING"),
            waitlistEntries.countByTenantIdAndStatus(tenantId, "OFFERED"),
            waitlistEntries.countByTenantIdAndStatus(tenantId, "FULFILLED"),
            waitlistEntries.countByTenantIdAndStatus(tenantId, "EXPIRED")),
        new OutboxSummary(
            outbox.countByTenantIdAndStatus(tenantId, "PENDING"),
            outbox.countByTenantIdAndStatus(tenantId, "FAILED")));
  }
}
