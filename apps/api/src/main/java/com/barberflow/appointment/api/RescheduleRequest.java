package com.barberflow.appointment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Novo horario da reserva no reagendamento. */
public record RescheduleRequest(@NotNull Instant startsAt) {}
