package com.barberflow.appointment.application;

import com.barberflow.availability.api.Slot;
import java.util.List;

/**
 * Conflito de horario: intervalo ocupado por reserva ativa ou bloqueio.
 * Respondida como 409 (problem+json) com horarios alternativos (spec, secao 7.6).
 */
public class AppointmentConflictException extends RuntimeException {

  private final transient List<Slot> alternatives;

  public AppointmentConflictException(String message, List<Slot> alternatives) {
    super(message);
    this.alternatives = alternatives;
  }

  public List<Slot> getAlternatives() {
    return alternatives;
  }
}
