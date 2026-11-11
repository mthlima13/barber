package com.barberflow.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Entrada de login da equipe. */
public record LoginRequest(
    @NotBlank @Email String email, @NotBlank String password) {}
