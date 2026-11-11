package com.barberflow.waitlist.api;

import com.barberflow.waitlist.application.WaitlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inscricao na lista de espera (publica, spec, secao 10). Telefone verificado
 * + consentimento entram na Fase 4 (antifraude); rate limit tambem.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicWaitlistController {

  private final WaitlistService waitlist;

  public PublicWaitlistController(WaitlistService waitlist) {
    this.waitlist = waitlist;
  }

  @PostMapping("/waitlist")
  @ResponseStatus(HttpStatus.CREATED)
  public WaitlistEntryResponse join(@Valid @RequestBody CreateWaitlistRequest request) {
    return waitlist.join(request);
  }
}
