package com.barberflow.staff.api;

import com.barberflow.service.api.ServiceResponse;
import com.barberflow.staff.application.BarberAppService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD de profissionais e habilitacao de servicos (painel do gestor) — spec, secao 10.
 *
 * <p>TODO(auth): exigir papel MANAGER quando o modulo auth estiver pronto.</p>
 */
@RestController
@RequestMapping("/api/v1/dashboard/barbers")
public class BarberController {

  private final BarberAppService barbers;

  public BarberController(BarberAppService barbers) {
    this.barbers = barbers;
  }

  @PostMapping
  public ResponseEntity<BarberResponse> create(@Valid @RequestBody BarberRequest request) {
    BarberResponse created = barbers.create(request);
    return ResponseEntity.created(URI.create("/api/v1/dashboard/barbers/" + created.id()))
        .body(created);
  }

  @GetMapping
  public List<BarberResponse> list() {
    return barbers.list();
  }

  @GetMapping("/{id}")
  public BarberResponse get(@PathVariable UUID id) {
    return barbers.get(id);
  }

  @PatchMapping("/{id}")
  public BarberResponse patch(@PathVariable UUID id, @Valid @RequestBody BarberPatchRequest request) {
    return barbers.patch(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    barbers.deactivate(id);
  }

  @GetMapping("/{id}/services")
  public List<ServiceResponse> listServices(@PathVariable UUID id) {
    return barbers.listEnabledServices(id);
  }

  @PutMapping("/{id}/services")
  public List<ServiceResponse> enableServices(
      @PathVariable UUID id, @Valid @RequestBody EnableServicesRequest request) {
    return barbers.enableServices(id, request.serviceIds());
  }
}
