package com.barberflow.availability.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Disponibilidade estimada de um profissional para uma data (spec, secao 7.4:
 * a interface mostra estimativa; a confirmacao revalida no servidor).
 */
public record AvailabilityResponse(
    LocalDate date,
    String timezone,
    UUID barberId,
    UUID serviceId,
    int durationMinutes,
    List<Slot> slots) {}
