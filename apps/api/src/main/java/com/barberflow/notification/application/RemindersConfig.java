package com.barberflow.notification.application;

import com.barberflow.notification.infrastructure.ReminderProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Lembrete 24h (Fase 4): properties e agendamento do job. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ReminderProperties.class)
public class RemindersConfig {}
