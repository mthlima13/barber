package com.barberflow.auth.api;

import com.barberflow.auth.application.AuthService;
import com.barberflow.auth.domain.AuthPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticacao (spec, secao 10): login da equipe, bootstrap do
 * primeiro gestor e identidade da sessao.
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/auth/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return auth.login(request.email(), request.password());
  }

  @PostMapping("/auth/bootstrap")
  public ResponseEntity<LoginResponse> bootstrap(@Valid @RequestBody BootstrapRequest request) {
    LoginResponse created = auth.bootstrap(request.name(), request.email(), request.password());
    return ResponseEntity.created(URI.create("/api/v1/me")).body(created);
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
    return auth.me(principal);
  }
}
