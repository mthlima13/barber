package com.barberflow.appointment.api;

import com.barberflow.appointment.application.AppointmentAppService;
import com.barberflow.appointment.application.AppointmentAppService.CreateResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Criacao de reserva pelo cliente (publico, spec, secao 9.1).
 *
 * <p>TODO(fase-4): rate limit e verificacao do telefone antes do lancamento
 * publico. TODO(fase-4): evento APPOINTMENT_CREATED na outbox na mesma
 * transacao.</p>
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicAppointmentController {

  private final AppointmentAppService appointments;

  public PublicAppointmentController(AppointmentAppService appointments) {
    this.appointments = appointments;
  }

  /**
   * Reserva nova -> 201 com Location; replay idempotente (mesma
   * Idempotency-Key) -> 200 (spec, secao 10).
   */
  @PostMapping("/appointments")
  public ResponseEntity<AppointmentResponse> create(
      @Valid @RequestBody CreateAppointmentRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    CreateResult result = appointments.create(request, idempotencyKey);
    if (!result.created()) {
      return ResponseEntity.ok(result.appointment());
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .location(URI.create("/api/v1/customer/appointments/" + result.appointment().id()))
        .body(result.appointment());
  }
}
