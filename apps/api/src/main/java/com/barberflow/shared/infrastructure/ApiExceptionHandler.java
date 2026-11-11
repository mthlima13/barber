package com.barberflow.shared.infrastructure;

import com.barberflow.appointment.application.AppointmentConflictException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros de dominio como problem+json (RFC 9457). Conflitos de horario incluem
 * horarios alternativos (spec, secao 7.6).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(AppointmentConflictException.class)
  ProblemDetail onAppointmentConflict(AppointmentConflictException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setTitle("Conflito de horario");
    problem.setProperty("alternativeSlots", exception.getAlternatives());
    return problem;
  }
}
