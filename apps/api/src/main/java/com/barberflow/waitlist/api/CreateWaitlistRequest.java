package com.barberflow.waitlist.api;

import com.barberflow.appointment.api.CustomerInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Inscricao na lista de espera (spec, secoes 9.2 e 10): alerta para um
 * horario especifico. O telefone e verificado antes de confirmar conforme
 * estrategia antifraude (TODO fase-4).
 */
public record CreateWaitlistRequest(
    @NotBlank String slug,
    @NotNull UUID barberId,
    @NotNull UUID serviceId,
    @NotNull Instant startsAt,
    @NotNull CustomerInput customer) {}
