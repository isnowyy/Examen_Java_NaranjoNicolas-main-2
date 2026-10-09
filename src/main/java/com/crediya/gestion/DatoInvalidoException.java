package com.crediya.gestion;

/**
 * Un prestamo no cumple las reglas para entrar al gestor o a la base de datos:
 * monto o plazo que no son mayores que 0, un estado distinto de "Activo",
 * "Pagado" o "Mora", un cliente que no existe.
 *
 * <p>Las demas excepciones del sistema son no verificadas porque ninguna capa
 * intermedia sabria recuperarse de ellas. Esta es verificada a proposito: un
 * dato invalido es un error esperado del que quien llama siempre puede
 * recuperarse (avisar y seguir con el siguiente prestamo), y el
 * {@code throws} obliga al compilador a recordarselo.</p>
 */
public class DatoInvalidoException extends Exception {

    private static final long serialVersionUID = 1L;

    public DatoInvalidoException(String mensaje) {
        super(mensaje);
    }

    public DatoInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
