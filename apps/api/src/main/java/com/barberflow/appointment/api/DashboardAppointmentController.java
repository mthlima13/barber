package com.barberflow.appointment.api;

import com.barberflow.appointment.application.AppointmentAppService;
import com.barberflow.tenant.application.TenantService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agenda do dashboard (spec, secao 10): listagem por dia e transicoes de
 * status respeitando a maquina de estados (secao 8).
 *
 * <p>TODO(fase-2+): acesso BARBER restrito a propria agenda (hoje o dashboard
 * exige MANAGER).</p>
 */
@RestController
@RequestMapping("/api/v1/dashboard/appointments")
public class DashboardAppointmentController {

  private final AppointmentAppService appointments;
  private final TenantService tenants;

  public DashboardAppointmentController(
      AppointmentAppService appointments, TenantService tenants) {
    this.appointments = appointments;
    this.tenants = tenants;
  }

  @GetMapping
  public List<AppointmentResponse> agenda(
      @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date,
      @RequestParam(required = false) UUID barberId) {
    return appointments.agenda(tenants.requireCurrentTenantId(), date, barberId);
  }

  @PatchMapping("/{id}/status")
  public AppointmentResponse updateStatus(
      @PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request) {
    return appointments.updateStatus(
        tenants.requireCurrentTenantId(), id, request.status());
  }
}
