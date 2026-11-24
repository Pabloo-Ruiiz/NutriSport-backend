package com.nutrisport.comun.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas ({@code @Scheduled}) salvo con {@code app.tareas.activas=false} (en los
 * tests, que las ejecutan a mano con fechas controladas). Las tareas usan la zona {@code app.zona-horaria}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.tareas.activas", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
