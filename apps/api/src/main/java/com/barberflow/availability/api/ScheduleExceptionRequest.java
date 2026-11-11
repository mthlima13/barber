package com.barberflow.availability.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Excecao de agenda (folga ou bloqueio pontual), intervalo semiaberto
 * {@code [startAt, endAt)} em UTC.
 */
public record ScheduleExceptionRequest(
    @NotNull Instant startAt,
    @NotNull Instant endAt,
    @NotBlank String kind) {}
