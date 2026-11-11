package com.barberflow.appointment.api;

import com.barberflow.appointment.application.AppointmentAppService;
import com.barberflow.auth.domain.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gerenciamento da reserva pelo cliente, autenticado pelo link seguro
 * (token de gerenciamento emitido na criacao — spec, secao 9.1).
 */
@RestController
@RequestMapping("/api/v1/customer/appointments")
public class CustomerAppointmentController {

  private final AppointmentAppService appointments;

  public CustomerAppointmentController(AppointmentAppService appointments) {
    this.appointments = appointments;
  }

  @GetMapping("/{id}")
  public AppointmentResponse get(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
    return appointments.getForCustomer(principal, id);
  }

  @PostMapping("/{id}/cancel")
  public AppointmentResponse cancel(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
    return appointments.cancelForCustomer(principal, id);
  }

  @PostMapping("/{id}/reschedule")
  public AppointmentResponse reschedule(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody RescheduleRequest request) {
    return appointments.rescheduleForCustomer(principal, id, request.startsAt());
  }
}
