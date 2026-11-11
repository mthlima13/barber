package com.barberflow.auth.domain;

import java.util.UUID;

/**
 * Identidade autenticada extraida do token de acesso.
 *
 * @param userId   id do usuario (tabela {@code users})
 * @param tenantId tenant da sessao (spec, secao 5: tenant vem do token, nunca do cliente)
 * @param role     papel na barbearia (MANAGER ou BARBER)
 */
public record AuthPrincipal(UUID userId, UUID tenantId, String role) {}
