package com.barberflow.appointment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados minimos do cliente (telefone E.164 normalizado) + consentimento
 * LGPD: o aceite da politica de privacidade e obrigatoria na coleta
 * (spec, linha 148 e linha 223).
 */
public record CustomerInput(
    @NotBlank @Size(max = 120) String name,
    @NotBlank
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "telefone deve estar em formato E.164")
        String phone,
    @NotNull boolean consented) {}
