package com.barberflow.appointment.api;

import com.barberflow.appointment.application.AppointmentAppService;
import com.barberflow.appointment.application.AppointmentAppService.CreateResult;
import jakarta.validation.Valid;
import java.net.URI;
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
 * <p>Idempotency-Key obrigatoria (spec, linha 149 e padroes da linha 200:
 * "Idempotency-Key em mutacoes criticas") — cabecalho ausente responde 400.
 * Rate limit aplicado pelo {@link com.barberflow.shared.ratelimit.RateLimitFilter}.
 * TODO(fase-4): verificacao do telefone antes do lancamento publico.</p>
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
   * Idempotency-Key) -> 200 (spec, secao 10). Header ausente -> 400.
   */
  @PostMapping("/appointments")
  public ResponseEntity<AppointmentResponse> create(
      @Valid @RequestBody CreateAppointmentRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    CreateResult result = appointments.create(request, idempotencyKey);
    if (!result.created()) {
      return ResponseEntity.ok(result.appointment());
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .location(URI.create("/api/v1/customer/appointments/" + result.appointment().id()))
        .body(result.appointment());
  }
}
