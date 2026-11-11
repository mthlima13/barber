package com.barberflow.staff.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Entrada para criacao de profissional. */
public record BarberRequest(
    @NotBlank @Size(max = 120) String displayName,
    UUID userId) {}
