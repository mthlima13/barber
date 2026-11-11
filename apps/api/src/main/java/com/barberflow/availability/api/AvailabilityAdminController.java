package com.barberflow.availability.api;

import com.barberflow.availability.application.AvailabilityAdminService;
import com.barberflow.tenant.application.TenantService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestao do expediente e de excecoes de agenda (painel do gestor).
 */
@RestController
@RequestMapping("/api/v1/dashboard/barbers/{barberId}")
public class AvailabilityAdminController {

  private final AvailabilityAdminService availability;
  private final TenantService tenants;

  public AvailabilityAdminController(
      AvailabilityAdminService availability, TenantService tenants) {
    this.availability = availability;
    this.tenants = tenants;
  }

  @GetMapping("/hours")
  public List<BusinessHoursResponse> listHours(@PathVariable UUID barberId) {
    return availability.listHours(tenants.requireCurrentTenantId(), barberId);
  }

  @PutMapping("/hours")
  public List<BusinessHoursResponse> replaceHours(
      @PathVariable UUID barberId, @Valid @RequestBody List<BusinessHoursRequest> requests) {
    return availability.replaceHours(tenants.requireCurrentTenantId(), barberId, requests);
  }

  @GetMapping("/exceptions")
  public List<ScheduleExceptionResponse> listExceptions(@PathVariable UUID barberId) {
    return availability.listExceptions(tenants.requireCurrentTenantId(), barberId);
  }

  @PostMapping("/exceptions")
  public ResponseEntity<ScheduleExceptionResponse> createException(
      @PathVariable UUID barberId, @Valid @RequestBody ScheduleExceptionRequest request) {
    var created = availability.createException(tenants.requireCurrentTenantId(), barberId, request);
    return ResponseEntity.created(
            URI.create("/api/v1/dashboard/barbers/" + barberId + "/exceptions/" + created.id()))
        .body(created);
  }

  @DeleteMapping("/exceptions/{exceptionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteException(
      @PathVariable UUID barberId, @PathVariable UUID exceptionId) {
    availability.deleteException(tenants.requireCurrentTenantId(), barberId, exceptionId);
  }
}
