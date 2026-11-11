package com.barberflow.waitlist.api;

import com.barberflow.waitlist.application.WaitlistService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gerenciamento da propria inscricao pelo link seguro (token de gestao
 * emitido na inscricao — spec, secao 10).
 */
@RestController
@RequestMapping("/api/v1/customer/waitlist")
public class CustomerWaitlistController {

  private final WaitlistService waitlist;

  public CustomerWaitlistController(WaitlistService waitlist) {
    this.waitlist = waitlist;
  }

  @GetMapping("/{id}")
  public WaitlistEntryResponse get(
      @AuthenticationPrincipal com.barberflow.auth.domain.AuthPrincipal principal,
      @PathVariable UUID id) {
    return waitlist.forCustomer(principal, id);
  }

  @DeleteMapping("/{id}")
  public WaitlistEntryResponse leave(
      @AuthenticationPrincipal com.barberflow.auth.domain.AuthPrincipal principal,
      @PathVariable UUID id) {
    return waitlist.leave(principal, id);
  }
}
