package com.barberflow.availability.api;

import com.barberflow.availability.application.AvailabilityQueryService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disponibilidade publica (spec, secao 10): barbearia identificada pelo slug.
 *
 * <p>TODO(fase-4): rate limit por IP antes do lancamento publico.</p>
 */
@RestController
@RequestMapping("/api/v1/public/barbershops")
public class AvailabilityController {

  private final AvailabilityQueryService availability;

  public AvailabilityController(AvailabilityQueryService availability) {
    this.availability = availability;
  }

  @GetMapping("/{slug}/availability")
  public AvailabilityResponse availability(
      @PathVariable String slug,
      @RequestParam UUID barberId,
      @RequestParam UUID serviceId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return availability.bySlug(slug, barberId, serviceId, date);
  }
}
