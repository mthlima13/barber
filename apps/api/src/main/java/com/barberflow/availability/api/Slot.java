package com.barberflow.availability.api;

import java.time.Instant;
import java.util.UUID;

/** Intervalo livre: {@code [startAt, endAt)} em UTC. */
public record Slot(Instant startAt, Instant endAt) {}
