package com.barberflow.shared.infrastructure;

import com.barberflow.auth.infrastructure.JwtAuthenticationFilter;
import com.barberflow.auth.infrastructure.JwtService;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguranca do MVP (spec, secoes 10 e 11): sessoes sem estado via token Bearer
 * (JWT HS256), dashboard restrito a MANAGER, rotas de autenticacao e
 * healthcheck publicas.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService)
      throws Exception {
    return http
        .authorizeHttpRequests(
            auth ->
                auth
                    // Healthcheck para orquestracao/compose
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    // Login da equipe e bootstrap do primeiro gestor
                    .requestMatchers("/api/v1/auth/login", "/api/v1/auth/bootstrap")
                    .permitAll()
                    // Painel do gestor (agenda do barbeiro entra depois: BARBER)
                    .requestMatchers("/api/v1/dashboard/**")
                    .hasRole("MANAGER")
                    // Qualquer outro endpoint exige sessao valida (ex.: /api/v1/me)
                    .anyRequest()
                    .authenticated())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(
                    (request, response, ex) -> {
                      response.setStatus(HttpStatus.UNAUTHORIZED.value());
                      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                      response
                          .getWriter()
                          .write(
                              "{\"title\":\"Nao autenticado\",\"status\":401,"
                                  + "\"detail\":\"Envie o header Authorization: Bearer <token>\"}");
                    }))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
