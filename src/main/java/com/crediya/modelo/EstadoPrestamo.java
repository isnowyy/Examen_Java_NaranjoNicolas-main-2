package com.crediya.modelo;

import com.crediya.excepcion.ValidacionException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Estado contable de un prestamo.
 *
 * <p>Los tres valores son los que define el enunciado para la columna
 * {@code estado}: "Activo", "Pagado" y "Mora". Se guardan con ese mismo texto
 * ({@link #getValor()}) en la base y en los archivos, de modo que la tabla
 * {@code prestamos} tiene un solo vocabulario sin importar si la fila la
 * escribio este modelo o el gestor en memoria de {@code com.crediya.gestion}.</p>
 *
 * <p>MORA es una marca que pone cobranza (o el gestor al cargar un credito que
 * ya venia atrasado). No es la unica forma de estar en mora: un prestamo ACTIVO
 * cuya fecha de vencimiento ya paso tambien se considera vencido. Las dos
 * situaciones se resuelven en {@link Prestamo#estaVencido}.</p>
 */
public enum EstadoPrestamo {

    ACTIVO("Activo", "Vigente, con saldo por cobrar"),
    PAGADO("Pagado", "Cancelado en su totalidad"),
    MORA("Mora", "En mora, marcado por cobranza");

    /** Nombre que tenia ACTIVO antes de adoptar el vocabulario del enunciado. */
    private static final String NOMBRE_ANTERIOR_DE_ACTIVO = "PENDIENTE";

    private final String valor;
    private final String descripcion;

    EstadoPrestamo(String valor, String descripcion) {
        this.valor = valor;
        this.descripcion = descripcion;
    }

    /** Texto con el que se guarda: "Activo", "Pagado" o "Mora". */
    public String getValor() {
        return valor;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Interpreta el texto guardado sin distinguir mayusculas: "Activo",
     * "ACTIVO" y "activo" son el mismo estado.
     *
     * <p>Tambien acepta "PENDIENTE", el nombre anterior de ACTIVO, para que
     * los archivos de texto escritos por versiones previas se sigan leyendo.</p>
     */
    public static EstadoPrestamo desde(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ValidacionException("El estado es obligatorio. Opciones: " + opciones());
        }
        String limpio = texto.trim();
        if (NOMBRE_ANTERIOR_DE_ACTIVO.equalsIgnoreCase(limpio)) {
            return ACTIVO;
        }
        return Arrays.stream(values())
                .filter(estado -> estado.valor.equalsIgnoreCase(limpio))
                .findFirst()
                .orElseThrow(() -> new ValidacionException(
                        "Estado desconocido: '" + texto + "'. Opciones: " + opciones()));
    }

    public static String opciones() {
        return Arrays.stream(values()).map(EstadoPrestamo::getValor).collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
