package com.barberflow.auth.api;

import java.util.UUID;

/** Identidade do usuario autenticado. */
public record MeResponse(
    UUID id, String name, String email, UUID tenantId, String role) {}
