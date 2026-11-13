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
                    // Pagina de erro: dispatch de erro deve preservar o status
                    // real (ex.: 500), nao virar 401 do filtro de JWT
                    .requestMatchers("/error")
                    .permitAll()
                    // Endpoints publicos (disponibilidade; rate limit em fase posterior)
                    .requestMatchers("/api/v1/public/**")
                    .permitAll()
                    // Aceite de oferta de lista de espera: o token unico do
                    // caminho e a propria credencial (spec, secao 10)
                    .requestMatchers("/api/v1/customer/offers/*/accept")
                    .permitAll()
                    // Webhook do WhatsApp: protegido por assinatura
                    // X-Hub-Signature-256 no proprio controller (spec, linha 221)
                    .requestMatchers("/api/v1/webhooks/whatsapp")
                    .permitAll()
                    // Agenda do PROPRIO profissional (papel BARBER, spec
                    // linha 218/361): antes do matcher geral do dashboard
                    .requestMatchers("/api/v1/dashboard/barber/**")
                    .hasAnyRole("BARBER", "MANAGER")
                    // Painel do gestor (agenda do barbeiro entra depois: BARBER)
                    .requestMatchers("/api/v1/dashboard/**")
                    .hasRole("MANAGER")
                    // Qualquer outro endpoint exige sessao valida (ex.: /api/v1/me)
                    .anyRequest()
                    .authenticated())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers ->
                headers
                    // Spec, linha 216: CSP e headers de seguranca. A API
                    // serve JSON (sem HTML proprio): nada embute a API.
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                    // Nenhuma resposta da API deve ser cacheada por proxies
                    .cacheControl(cache -> {}))
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
