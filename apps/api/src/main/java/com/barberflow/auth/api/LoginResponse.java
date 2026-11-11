package com.barberflow.auth.api;

import java.util.UUID;

/** Resposta de login/bootstrap com o token de acesso (spec, secao 11). */
public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    MeResponse user,
    UUID tenantId,
    String role) {}
