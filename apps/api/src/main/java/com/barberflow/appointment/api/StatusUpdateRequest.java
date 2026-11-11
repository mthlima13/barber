package com.barberflow.appointment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Atualizacao de status pelo dashboard (spec, secao 8). */
public record StatusUpdateRequest(
    @NotBlank
        @Pattern(
            regexp = "CONFIRMED|COMPLETED|CANCELLED|NO_SHOW",
            message = "status invalido")
        String status) {}
