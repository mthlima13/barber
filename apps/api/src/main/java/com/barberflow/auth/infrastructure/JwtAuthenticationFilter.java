package com.barberflow.auth.infrastructure;

import com.barberflow.auth.domain.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Popula o SecurityContext a partir do token Bearer, quando presente. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      try {
        AuthPrincipal principal = jwtService.verify(header.substring("Bearer ".length()));
        var authentication =
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (RuntimeException e) {
        // Token invalido/expirado: segue sem autenticacao; endpoints protegidos
        // respondem 401 pelo authenticationEntryPoint.
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
