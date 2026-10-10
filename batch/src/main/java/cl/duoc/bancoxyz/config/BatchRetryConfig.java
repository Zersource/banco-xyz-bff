package cl.duoc.bancoxyz.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;

/**
 * Politica de espera entre reintentos, compartida por los 3 Jobs (mismo
 * criterio que {@link BatchThreadingConfig#batchTaskExecutor()}: un solo
 * bean centraliza la configuracion en vez de repetirla en cada
 * {@code *JobConfig}).
 * <p>
 * Semana 2 dejo {@code .retry(TransientDataAccessException.class).retryLimit(3)}
 * sin backoff: el segundo y tercer intento salian de inmediato, uno detras
 * de otro. Para un error transitorio de conexion a base de datos eso no
 * sirve de mucho — si la caida dura un par de cientos de milisegundos,
 * reintentar sin esperar cae en el mismo problema. Semana 3 pide politicas
 * de reintento explicitas (no solo el retry basico), asi que se agrega un
 * {@link ExponentialBackOffPolicy} para espaciar los reintentos:
 * <ul>
 *     <li>{@code initialInterval}: 200 ms antes del primer reintento.</li>
 *     <li>{@code multiplier}: 2.0, cada intento siguiente espera el doble
 *     que el anterior (200 ms, 400 ms, 800 ms para retryLimit(3)).</li>
 *     <li>{@code maxInterval}: 2000 ms, tope para no alargar demasiado un
 *     Step si el problema persiste igual hasta agotar el retryLimit.</li>
 * </ul>
 * Con retryLimit(3) esto da tiempo real para que una caida momentanea de
 * conexion se recupere, sin bloquear el Step por mas de ~1.4 segundos en
 * el peor caso antes de pasar a skip.
 */
@Configuration
public class BatchRetryConfig {

    @Bean
    public BackOffPolicy transientErrorBackOffPolicy() {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(200L);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(2000L);
        return backOffPolicy;
    }
}
