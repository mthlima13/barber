package com.barberflow.staff.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credenciais de acesso do profissional (criadas pelo gestor). */
public record BarberCredentialsRequest(
    @NotBlank @jakarta.validation.constraints.Email String email,
    @NotBlank @Size(min = 8, max = 120) String password) {}
