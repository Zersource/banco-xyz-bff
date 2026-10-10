package cl.duoc.bancoxyz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pool de hilos usado por el particionado de transacciones (via
 * TaskExecutorPartitionHandler). Intereses y estados-cuenta dejaron de
 * usarlo en semana 3 — ver el javadoc de InteresesMensualesJobConfig para
 * el motivo (deadlock real combinando multi-hilo con fault-tolerant, no
 * particionado).
 * <p>
 * {@code corePoolSize}/{@code maxPoolSize} se leen de
 * {@code app.batch.thread-pool-size} (default 3, el valor fijo usado en
 * semana 2) para poder comparar distintas cantidades de particiones en el
 * benchmark de semana 3 sin recompilar: {@code --app.batch.thread-pool-size=5}.
 * {@code queueCapacity} en 0 sigue siendo intencional: el particionado ya
 * limita la concurrencia con {@code gridSize} (ver
 * {@code TransaccionesDiariasJobConfig}), asi que no hace falta cola de
 * espera adicional.
 */
@Configuration
public class BatchThreadingConfig {

    @Value("${app.batch.thread-pool-size:3}")
    private int threadPoolSize;

    @Bean
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("batch-thread-");
        executor.initialize();
        return executor;
    }
}

