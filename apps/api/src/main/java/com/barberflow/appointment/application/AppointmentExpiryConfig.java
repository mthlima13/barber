package com.barberflow.appointment.application;

import com.barberflow.appointment.infrastructure.AppointmentExpiryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Expiracao de PENDING (spec, linha 137): properties do job. */
@Configuration
@EnableConfigurationProperties(AppointmentExpiryProperties.class)
public class AppointmentExpiryConfig {}
