package com.barberflow.staff.api;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Corpo do PUT que substitui os servicos habilitados de um profissional. */
public record EnableServicesRequest(@NotNull List<UUID> serviceIds) {}
