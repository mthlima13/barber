package com.barberflow.appointment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Criacao de reserva pelo cliente (spec, secao 9.1). {@code startsAt} em ISO
 * 8601 (UTC ou offset); a duracao vem do servico (snapshot).
 */
public record CreateAppointmentRequest(
    @NotBlank String slug,
    @NotNull UUID barberId,
    @NotNull UUID serviceId,
    @NotNull Instant startsAt,
    @NotNull CustomerInput customer) {}
