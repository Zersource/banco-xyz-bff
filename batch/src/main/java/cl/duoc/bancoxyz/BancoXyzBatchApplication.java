package cl.duoc.bancoxyz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicacion batch del Banco XYZ.
 * <p>
 * El arranque de los Jobs NO es automatico (spring.batch.job.enabled=false),
 * ya que existen multiples Jobs definidos en el contexto. La seleccion y el
 * lanzamiento del Job correspondiente se realiza en
 * {@link cl.duoc.bancoxyz.config.JobLauncherRunner}, a partir del argumento
 * recibido por linea de comandos.
 */
@SpringBootApplication
public class BancoXyzBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BancoXyzBatchApplication.class, args);
    }
}
