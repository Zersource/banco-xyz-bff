package com.duoc.bancoxyzbff.interno;

import com.duoc.bancoxyzbff.model.Cuenta;
import com.duoc.bancoxyzbff.model.Transaccion;
import com.duoc.bancoxyzbff.service.CuentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints internos, consumidos por bff-movil y bff-cajero a traves de
 * Eureka (no son para clientes externos). Desde Exp3 S6, bff-web es el
 * unico BFF que mantiene acceso directo a los datos (CSV en memoria);
 * los otros dos BFF ya no cargan su propia copia de los datos, sino que
 * le piden la informacion a este servicio por HTTP, protegidos con
 * Circuit Breaker en el lado del que consume.
 *
 * No lleva CanalAuthInterceptor: es trafico interno entre microservicios,
 * no trafico de un canal externo (web/movil/cajero), que es lo que ese
 * interceptor protege. Queda documentado como una simplificacion propia
 * del alcance de esta semana (el foco es Config Server, Eureka y
 * Circuit Breaker, no seguridad de comunicacion entre servicios).
 */
@RestController
@RequestMapping("/interno")
public class InternoController {

    @Autowired
    private CuentaService cuentaService;

    @GetMapping("/cuentas/{cuentaId}")
    public Cuenta obtenerCuenta(@PathVariable Long cuentaId) {
        return cuentaService.obtenerCuentaPorId(cuentaId);
    }

    @GetMapping("/transacciones/ultimas")
    public List<Transaccion> obtenerUltimasTransacciones(@RequestParam(defaultValue = "5") int cantidad) {
        return cuentaService.obtenerUltimasTransacciones(cantidad);
    }

    @GetMapping("/cuentas/{cuentaId}/saldo")
    public Double consultarSaldo(@PathVariable Long cuentaId) {
        return cuentaService.consultarSaldo(cuentaId);
    }

    @PostMapping("/cuentas/{cuentaId}/retiro")
    public Double retirar(@PathVariable Long cuentaId, @RequestParam Double monto) {
        return cuentaService.realizarRetiro(cuentaId, monto);
    }
}
