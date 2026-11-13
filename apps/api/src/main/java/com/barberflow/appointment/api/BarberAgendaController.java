package com.barberflow.appointment.api;

import com.barberflow.appointment.application.AppointmentAppService;
import com.barberflow.auth.domain.AuthPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agenda do PROPRIO profissional (papel BARBER — spec, linhas 218 e 361:
 * autorizacao por papel e por recurso). O perfil do profissional vem do
 * token (user_id vinculado em barbers.user_id); o BARBER ve e altera
 * somente as proprias reservas e nunca acessa o CRUD da equipe/servicos
 * (403 em /api/v1/dashboard/**).
 */
@RestController
@RequestMapping("/api/v1/dashboard/barber")
public class BarberAgendaController {

  private final AppointmentAppService appointments;

  public BarberAgendaController(AppointmentAppService appointments) {
    this.appointments = appointments;
  }

  @GetMapping("/agenda")
  public List<AppointmentResponse> agenda(
      @AuthenticationPrincipal AuthPrincipal principal,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return appointments.barberAgenda(principal, date);
  }

  @PatchMapping("/appointments/{id}/status")
  public AppointmentResponse updateStatus(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody StatusUpdateRequest request) {
    return appointments.barberUpdateStatus(principal, id, request.status());
  }
}
