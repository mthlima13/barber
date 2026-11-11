package com.barberflow.appointment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dados minimos do cliente (telefone E.164 normalizado). */
public record CustomerInput(
    @NotBlank @Size(max = 120) String name,
    @NotBlank
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "telefone deve estar em formato E.164")
        String phone) {}
