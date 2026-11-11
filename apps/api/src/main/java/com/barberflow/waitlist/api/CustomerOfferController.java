package com.barberflow.waitlist.api;

import com.barberflow.appointment.api.AppointmentResponse;
import com.barberflow.waitlist.application.WaitlistService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aceite da oferta de horario (spec, secao 10): o token unico no caminho e a
 * credencial (autorizacao propria, sem Bearer).
 */
@RestController
@RequestMapping("/api/v1/customer/offers")
public class CustomerOfferController {

  private final WaitlistService waitlist;

  public CustomerOfferController(WaitlistService waitlist) {
    this.waitlist = waitlist;
  }

  /** Aceite cria reserva PENDING: 201, como no POST publico de agendamento. */
  @PostMapping("/{token}/accept")
  @ResponseStatus(HttpStatus.CREATED)
  public AppointmentResponse accept(@PathVariable String token) {
    return waitlist.accept(token);
  }
}
