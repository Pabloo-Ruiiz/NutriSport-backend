package com.nutrisport.comun.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Ejecución asíncrona de los análisis con IA (§4.3: "{@code @Async} con pool limitado"). El pool es
 * pequeño a propósito: cada análisis espera varios segundos al proveedor y no debe acaparar hilos.
 * Si la cola se llena, el análisis queda PENDIENTE y la tarea de huérfanos lo marca FALLIDO a los 2
 * minutos.
 * <p>
 * Con {@code app.ia.async-sincrono=true} (tests) el ejecutor es síncrono: el análisis se procesa en el
 * mismo hilo, justo después del commit, y el test puede comprobar el resultado sin esperas.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String EJECUTOR_IA = "ejecutorIa";

    @Bean(name = EJECUTOR_IA)
    public TaskExecutor ejecutorIa(@Value("${app.ia.async-sincrono:false}") boolean sincrono) {
        if (sincrono) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(2);
        ejecutor.setMaxPoolSize(4);
        ejecutor.setQueueCapacity(50);
        ejecutor.setThreadNamePrefix("ia-");
        ejecutor.setWaitForTasksToCompleteOnShutdown(true);
        ejecutor.setAwaitTerminationSeconds(30);
        ejecutor.initialize();
        return ejecutor;
    }
}
