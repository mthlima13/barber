package com.barberflow.availability.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * Janela de expediente semanal (uma por dia da semana no MVP).
 *
 * <p>{@code weekday}: 0=domingo ... 6=sabado; horarios locais "HH:mm".</p>
 */
public record BusinessHoursRequest(
    @NotNull @Min(0) @Max(6) Integer weekday,
    @NotNull LocalTime startLocal,
    @NotNull LocalTime endLocal) {}
