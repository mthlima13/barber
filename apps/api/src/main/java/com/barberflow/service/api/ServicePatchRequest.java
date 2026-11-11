package com.barberflow.service.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Entrada para atualizacao parcial de servico (campos nulos sao ignorados). */
public record ServicePatchRequest(
    @Size(max = 120) String name,
    @Size(max = 500) String description,
    @Positive @Max(480) Integer durationMinutes,
    @PositiveOrZero Long priceCents,
    Boolean active) {}
