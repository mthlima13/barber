package com.barberflow.staff.api;

import jakarta.validation.constraints.Size;

/** Entrada para atualizacao parcial de profissional (campos nulos sao ignorados). */
public record BarberPatchRequest(
    @Size(max = 120) String displayName,
    Boolean active) {}
