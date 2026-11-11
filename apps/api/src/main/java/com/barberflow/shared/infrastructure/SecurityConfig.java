package com.barberflow.shared.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracao minima de seguranca da Fase 0.
 *
 * <p>Apenas o healthcheck fica publico. As regras completas de autenticacao
 * (login da equipe, tokens de gerenciamento do cliente, OTP) serao definidas
 * no modulo {@code auth} conforme secoes 2 e 11 da especificacao.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
            .anyRequest().authenticated())
        .csrf(csrf -> csrf.disable())
        .httpBasic(Customizer.withDefaults())
        .build();
  }
}
