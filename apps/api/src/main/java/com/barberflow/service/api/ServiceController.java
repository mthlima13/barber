package com.barberflow.service.api;

import com.barberflow.service.application.ServiceAppService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD de servicos do painel (gestor) — spec, secao 10.
 *
 * <p>TODO(auth): exigir papel MANAGER quando o modulo auth estiver pronto.</p>
 */
@RestController
@RequestMapping("/api/v1/dashboard/services")
public class ServiceController {

  private final ServiceAppService services;

  public ServiceController(ServiceAppService services) {
    this.services = services;
  }

  @PostMapping
  public ResponseEntity<ServiceResponse> create(@Valid @RequestBody ServiceRequest request) {
    ServiceResponse created = services.create(request);
    return ResponseEntity.created(URI.create("/api/v1/dashboard/services/" + created.id()))
        .body(created);
  }

  @GetMapping
  public List<ServiceResponse> list() {
    return services.list();
  }

  @GetMapping("/{id}")
  public ServiceResponse get(@PathVariable UUID id) {
    return services.get(id);
  }

  @PatchMapping("/{id}")
  public ServiceResponse patch(
      @PathVariable UUID id, @Valid @RequestBody ServicePatchRequest request) {
    return services.patch(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    services.deactivate(id);
  }
}
