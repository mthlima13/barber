package com.barberflow.shared.infrastructure;

import com.barberflow.auth.infrastructure.JwtAuthenticationFilter;
import com.barberflow.auth.infrastructure.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

  /**
   * CORS: o web (Next.js) consome a API de origem propria no browser —
   * sem isso, o fetch publico falha ("Failed to fetch"). Origens
   * autorizadas por variavel (dev: http://localhost:3000; prod: dominio
   * real). Webhook da Meta e server-to-server, sem impacto.
   */
  @Bean
  CorsConfigurationSource barberflowCorsConfigurationSource(
      @Value("${barberflow.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
    config.setAllowedMethods(
        List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtService jwtService,
      @org.springframework.beans.factory.annotation.Qualifier("barberflowCorsConfigurationSource")
      CorsConfigurationSource corsSource)
      throws Exception {
    return http
        .cors(cors -> cors.configurationSource(corsSource))
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
