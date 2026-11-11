package com.barberflow.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Entrada do bootstrap do primeiro gestor da barbearia. */
public record BootstrapRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 128) String password) {}
