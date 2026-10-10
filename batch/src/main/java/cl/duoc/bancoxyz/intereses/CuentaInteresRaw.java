package cl.duoc.bancoxyz.intereses;

/**
 * Fila cruda de {@code intereses.csv}. Todos los campos se leen como texto
 * para que el {@code ItemReader} no falle ante saldos vacios o edades
 * invalidas; la validacion la realiza {@link InteresItemProcessor}.
 */
public class CuentaInteresRaw {

    private String cuentaId;
    private String nombre;
    private String saldo;
    private String edad;
    private String tipo;

    public String getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(String cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getSaldo() {
        return saldo;
    }

    public void setSaldo(String saldo) {
        this.saldo = saldo;
    }

    public String getEdad() {
        return edad;
    }

    public void setEdad(String edad) {
        this.edad = edad;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    @Override
    public String toString() {
        return "CuentaInteresRaw{cuentaId=%s, nombre=%s, saldo=%s, edad=%s, tipo=%s}"
                .formatted(cuentaId, nombre, saldo, edad, tipo);
    }
}
