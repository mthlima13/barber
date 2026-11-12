package com.barberflow.shared.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registro do {@link RateLimitFilter} antes de toda a cadeia de seguranca:
 * limite aplicado inclusive a rotas publicas e de login, sem autenticacao
 * previa. Limites por minuto em janela deslizante de 60s.
 */
@Configuration
public class RateLimitConfig {

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
      @Value("${barberflow.ratelimit.public-read-per-minute:60}") int publicReadPerMinute,
      @Value("${barberflow.ratelimit.public-write-per-minute:20}") int publicWritePerMinute,
      @Value("${barberflow.ratelimit.auth-per-minute:10}") int authPerMinute) {
    FilterRegistrationBean<RateLimitFilter> registration =
        new FilterRegistrationBean<>(
            new RateLimitFilter(publicReadPerMinute, publicWritePerMinute, authPerMinute));
    registration.addUrlPatterns("/api/v1/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
