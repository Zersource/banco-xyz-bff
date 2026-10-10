package cl.duoc.bancoxyz.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Selecciona y lanza el/los Job(s) indicados por linea de comandos.
 * <p>
 * Como el arranque automatico de Spring Batch esta deshabilitado
 * ({@code spring.batch.job.enabled=false}, necesario porque el contexto
 * tiene tres Jobs), este runner es el punto de entrada explicito.
 * <p>
 * Uso:
 * <pre>
 *   java -jar banco-xyz-batch.jar transacciones
 *   java -jar banco-xyz-batch.jar intereses
 *   java -jar banco-xyz-batch.jar estados-cuenta
 *   java -jar banco-xyz-batch.jar todos
 * </pre>
 * <p>
 * <b>Semana 3 — System.exit() al terminar:</b> antes este metodo terminaba
 * sin llamar System.exit(), asi que el proceso quedaba a merced de que
 * todos los hilos no-daemon murieran solos para que la JVM cerrara. El
 * pool de {@link BatchThreadingConfig} (usado por el particionado de
 * transacciones) crea hilos no-daemon que quedan vivos esperando trabajo
 * indefinidamente una vez que procesaron al menos una particion — asi que
 * cualquier corrida que particionara transacciones se quedaba colgada para
 * siempre despues de terminar, sin importar si el Job resultaba exitoso o
 * fallido. Se descubrio al correr el benchmark de escalado contra el
 * dataset de semana 3.
 * <p>
 * Llamar System.exit(codigo) al final dispara el shutdown hook que Spring
 * Boot registra automaticamente sobre el contexto, que a su vez cierra
 * (destroy()) todos los beans, incluido el TaskExecutor — asi el pool se
 * apaga ordenadamente en vez de quedar colgando el proceso. De paso corrige
 * que antes el proceso siempre devolvia codigo 0 aunque el Job fallara.
 */
@Component
public class JobLauncherRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(JobLauncherRunner.class);

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("dailyTransactionsJob")
    private Job dailyTransactionsJob;

    @Autowired
    @Qualifier("monthlyInterestJob")
    private Job monthlyInterestJob;

    @Autowired
    @Qualifier("annualStatementJob")
    private Job annualStatementJob;

    private final Map<String, Job> jobsPorAlias = new LinkedHashMap<>();

    /**
     * Arma el mapa alias-Job una vez que Spring ya inyecto los tres campos
     * {@code @Autowired}. No puede hacerse en el constructor porque, al
     * inyectar por campo, los campos todavia no tienen valor cuando el
     * constructor (implicito, sin argumentos) se ejecuta.
     */
    @PostConstruct
    private void inicializarJobsPorAlias() {
        jobsPorAlias.put("transacciones", dailyTransactionsJob);
        jobsPorAlias.put("intereses", monthlyInterestJob);
        jobsPorAlias.put("estados-cuenta", annualStatementJob);
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            imprimirAyuda();
            return;
        }

        String seleccion = args[0].trim().toLowerCase();

        if ("todos".equals(seleccion)) {
            boolean huboFallas = false;
            for (Map.Entry<String, Job> entry : jobsPorAlias.entrySet()) {
                if (!ejecutar(entry.getKey(), entry.getValue())) {
                    huboFallas = true;
                }
            }
            System.exit(huboFallas ? 1 : 0);
            return;
        }

        Job job = jobsPorAlias.get(seleccion);
        if (job == null) {
            log.error("Argumento no reconocido: '{}'.", seleccion);
            imprimirAyuda();
            System.exit(1);
            return;
        }
        boolean exitoso = ejecutar(seleccion, job);
        System.exit(exitoso ? 0 : 1);
    }

    /** @return true si el JobExecution termino en BatchStatus.COMPLETED. */
    private boolean ejecutar(String alias, Job job) throws Exception {
        JobExecution jobExecution = jobLauncher.run(job, new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters());
        return jobExecution.getStatus() == BatchStatus.COMPLETED;
    }

    private void imprimirAyuda() {
        log.info("""

                Uso: java -jar banco-xyz-batch.jar <job>
                Jobs disponibles:
                  transacciones     -> Reporte de Transacciones Diarias
                  intereses         -> Calculo de Intereses Mensuales
                  estados-cuenta    -> Generacion de Estados de Cuenta Anuales
                  todos             -> Ejecuta los tres Jobs en secuencia
                """);
    }
}

