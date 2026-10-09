package com.crediya.gestion;

import com.crediya.modelo.EstadoPrestamo;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Prestamo que administra {@link GestorPrestamos}.
 *
 * <p>Es la version sencilla que pide el enunciado: solo conoce el
 * identificador del cliente y calcula el total con interes simple. El prestamo
 * completo del sistema, con asesor, abonos y tres modalidades de interes, es
 * {@link com.crediya.modelo.Prestamo}. Los dos se guardan en la misma tabla
 * {@code prestamos}.</p>
 *
 * <p>El estado es un texto, pero solo admite los tres valores del enunciado.
 * Esos valores salen de {@link EstadoPrestamo}, el mismo enum que usa el resto
 * del sistema, para que la tabla tenga un solo vocabulario.</p>
 *
 * <p>Monto y plazo no se validan aqui sino en
 * {@link GestorPrestamos#registrarPrestamo(Prestamo)}, que es donde el
 * enunciado pone esa regla: si el constructor ya los rechazara, la validacion
 * del gestor nunca llegaria a ejecutarse.</p>
 */
public class Prestamo {

    public static final String ACTIVO = EstadoPrestamo.ACTIVO.getValor();
    public static final String PAGADO = EstadoPrestamo.PAGADO.getValor();
    public static final String MORA = EstadoPrestamo.MORA.getValor();

    private static final List<String> ESTADOS = List.of(ACTIVO, PAGADO, MORA);
    private static final String OPCIONES = String.join(", ", ESTADOS);

    private int id;
    private int clienteId;
    private double monto;
    private double interesMensual;
    private int plazoMeses;
    private String estado;

    /**
     * Prestamo nuevo: queda en estado Activo y sin identificador (0), que lo
     * asigna el gestor al registrarlo.
     */
    public Prestamo(int clienteId, double monto, double interesMensual, int plazoMeses) {
        this.clienteId = clienteId;
        this.monto = monto;
        this.interesMensual = interesMensual;
        this.plazoMeses = plazoMeses;
        this.estado = ACTIVO;
    }

    /**
     * Prestamo con todos sus datos.
     *
     * @throws DatoInvalidoException si el estado no es "Activo", "Pagado" ni "Mora"
     */
    public Prestamo(int id, int clienteId, double monto, double interesMensual, int plazoMeses, String estado)
            throws DatoInvalidoException {
        this(clienteId, monto, interesMensual, plazoMeses);
        this.id = id;
        setEstado(estado);
    }

    /**
     * Capital mas intereses, con la formula del enunciado:
     * {@code total = monto + (monto * interesMensual / 100 * plazoMeses)}.
     *
     * <p>Es interes simple, la misma formula de
     * {@link com.crediya.modelo.interes.InteresSimple}: un prestamo guardado
     * por el gestor da el mismo total cuando lo lee el resto del sistema.</p>
     */
    public double calcularTotalAPagar() {
        return monto + (monto * interesMensual / 100 * plazoMeses);
    }

    // ------------------------------------------------------------------
    // Getters y setters
    // ------------------------------------------------------------------

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getClienteId() {
        return clienteId;
    }

    public void setClienteId(int clienteId) {
        this.clienteId = clienteId;
    }

    public double getMonto() {
        return monto;
    }

    public void setMonto(double monto) {
        this.monto = monto;
    }

    public double getInteresMensual() {
        return interesMensual;
    }

    public void setInteresMensual(double interesMensual) {
        this.interesMensual = interesMensual;
    }

    public int getPlazoMeses() {
        return plazoMeses;
    }

    public void setPlazoMeses(int plazoMeses) {
        this.plazoMeses = plazoMeses;
    }

    public String getEstado() {
        return estado;
    }

    /**
     * @param estado "Activo", "Pagado" o "Mora"; no distingue mayusculas y se
     *               guarda siempre con la forma del enunciado
     * @throws DatoInvalidoException si es otro valor
     */
    public void setEstado(String estado) throws DatoInvalidoException {
        this.estado = normalizarEstado(estado);
    }

    /**
     * Convierte "activo", " ACTIVO " o "Activo" en "Activo".
     *
     * @throws DatoInvalidoException si el texto no corresponde a ninguno de los tres estados
     */
    static String normalizarEstado(String estado) throws DatoInvalidoException {
        if (estado == null || estado.isBlank()) {
            throw new DatoInvalidoException("El estado es obligatorio. Valores posibles: " + OPCIONES + ".");
        }
        return ESTADOS.stream()
                .filter(valido -> valido.equalsIgnoreCase(estado.trim()))
                .findFirst()
                .orElseThrow(() -> new DatoInvalidoException(
                        "El estado '" + estado + "' no es válido. Valores posibles: " + OPCIONES + "."));
    }

    @Override
    public String toString() {
        return "ID: " + id
                + " - Cliente: " + clienteId
                + " - Monto: " + formatear(monto)
                + " - Interés: " + formatear(interesMensual) + "%"
                + " - Plazo: " + plazoMeses + (plazoMeses == 1 ? " mes" : " meses")
                + " - Total: " + formatear(calcularTotalAPagar());
    }

    /**
     * Escribe el numero con al menos un decimal y como maximo dos:
     * 500000.0 queda "500000.0" y 516666.666 queda "516666.67".
     *
     * <p>No se concatena el double directamente porque Java escribe los valores
     * desde diez millones en notacion cientifica (12000000.0 saldria "1.2E7").</p>
     */
    private static String formatear(double valor) {
        DecimalFormat formato = new DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(Locale.ROOT));
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor);
    }
}
