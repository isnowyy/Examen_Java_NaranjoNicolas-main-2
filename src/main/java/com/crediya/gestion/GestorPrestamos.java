package com.crediya.gestion;

import com.crediya.excepcion.PersistenciaException;
import com.crediya.repositorio.jdbc.ConexionBD;
import com.crediya.util.Bitacora;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Administra en memoria una lista de {@link Prestamo} y puede guardarlos en
 * la tabla {@code prestamos} de {@code crediya_db}.
 *
 * <p>La lista en memoria y la base son independientes: registrar un prestamo
 * no lo guarda en MySQL, y guardarlo en MySQL no lo agrega a la lista. Por eso
 * el identificador que asigna el gestor y el que asigna la base no tienen por
 * que coincidir.</p>
 */
public class GestorPrestamos {

    /** Limites de la tabla: {@code CHECK} de {@code sql/crediya_db.sql}. */
    private static final int PLAZO_MAXIMO = 120;
    private static final int INTERES_MAXIMO = 100;

    /**
     * Codigos de MySQL para una llave foranea que apunta a una fila
     * inexistente. Es el mismo error: 1452 trae el detalle de la llave y 1216
     * no, y MySQL elige uno u otro segun los privilegios del usuario. Con
     * {@code crediya_app}, el usuario de la aplicacion, llega el 1216.
     */
    private static final Set<Integer> MYSQL_LLAVE_FORANEA_INEXISTENTE = Set.of(1216, 1452);

    /**
     * Las columnas que el prestamo no tiene toman su valor por defecto en la
     * tabla: {@code fecha_inicio} es hoy, {@code tipo_interes} es SIMPLE (la
     * misma formula de {@link Prestamo#calcularTotalAPagar()}) y
     * {@code empleado_id} queda en {@code NULL}, sin asesor asignado.
     */
    private static final String SQL_INSERTAR = """
            INSERT INTO prestamos (cliente_id, monto, interes_mensual, plazo_meses, estado)
            VALUES (?, ?, ?, ?, ?)""";

    private final List<Prestamo> prestamos = new ArrayList<>();

    /** Siempre es mayor que cualquier identificador de la lista. */
    private int siguienteId = 1;

    /**
     * Agrega un prestamo a la lista.
     *
     * <p>Si llega sin identificador (0), el gestor le asigna el siguiente
     * libre. Si ya trae uno, se respeta mientras no este repetido.</p>
     *
     * @throws DatoInvalidoException si el monto o el plazo no son mayores que 0,
     *         si algun otro dato esta fuera de rango o si el identificador ya existe
     */
    public void registrarPrestamo(Prestamo p) throws DatoInvalidoException {
        validar(p);

        if (p.getId() < 0) {
            throw new DatoInvalidoException(
                    "El ID del préstamo no puede ser negativo (se recibió " + p.getId() + ").");
        }
        if (p.getId() == 0) {
            p.setId(siguienteId);
        } else if (existe(p.getId())) {
            throw new DatoInvalidoException("Ya hay un préstamo registrado con el ID " + p.getId() + ".");
        }

        prestamos.add(p);
        siguienteId = Math.max(siguienteId, p.getId() + 1);
    }

    /** Muestra en consola cada prestamo con su total calculado. */
    public void listarPrestamos() {
        if (prestamos.isEmpty()) {
            System.out.println("No hay préstamos registrados.");
            return;
        }
        prestamos.forEach(System.out::println);
    }

    /**
     * Prestamos cuyo estado coincide con el indicado, en el orden en que se
     * registraron.
     *
     * @param estado "Activo", "Pagado" o "Mora", sin distinguir mayusculas
     * @throws DatoInvalidoException si el estado no es ninguno de los tres
     */
    public List<Prestamo> buscarPorEstado(String estado) throws DatoInvalidoException {
        String buscado = Prestamo.normalizarEstado(estado);

        return prestamos.stream()
                .filter(prestamo -> prestamo.getEstado().equals(buscado))
                .toList();
    }

    /**
     * Vista de solo lectura de la lista: nadie de afuera puede agregar un
     * prestamo saltandose las validaciones de {@link #registrarPrestamo}.
     */
    public List<Prestamo> getPrestamos() {
        return Collections.unmodifiableList(prestamos);
    }

    /**
     * Inserta el prestamo en la tabla {@code prestamos} usando la conexion de
     * {@link ConexionBD}.
     *
     * <p>Un prestamo en estado Pagado se rechaza: en el sistema un prestamo
     * pasa a Pagado solo cuando sus abonos cubren el total, y uno que se esta
     * insertando todavia no tiene abonos en la tabla {@code pagos}. Guardarlo
     * asi dejaria un credito "pagado" que sigue debiendo todo su saldo.</p>
     *
     * <p>No modifica el prestamo: el identificador que asigna MySQL se
     * devuelve, sin reemplazar el que el prestamo tiene en la lista.</p>
     *
     * @return identificador que MySQL le asigno a la fila
     * @throws DatoInvalidoException si el prestamo no pasa las validaciones, si
     *         llega como Pagado o si el cliente no existe en la base
     * @throws PersistenciaException si no hay conexion con MySQL o la base
     *         rechaza la operacion por otro motivo
     */
    public int guardarPrestamoEnBD(Prestamo p) throws DatoInvalidoException {
        validar(p);
        if (Prestamo.PAGADO.equals(p.getEstado())) {
            throw new DatoInvalidoException("Un préstamo nuevo no puede guardarse como Pagado: "
                    + "pasa a ese estado cuando sus abonos cubren el total, y todavía no tiene ninguno.");
        }

        try (Connection conexion = ConexionBD.getInstancia().abrir();
             PreparedStatement sentencia = conexion.prepareStatement(
                     SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            sentencia.setInt(1, p.getClienteId());
            sentencia.setBigDecimal(2, aDecimal(p.getMonto()));
            sentencia.setBigDecimal(3, aDecimal(p.getInteresMensual()));
            sentencia.setInt(4, p.getPlazoMeses());
            sentencia.setString(5, p.getEstado());
            sentencia.executeUpdate();

            try (ResultSet llaves = sentencia.getGeneratedKeys()) {
                if (!llaves.next()) {
                    throw new PersistenciaException("MySQL no devolvió el ID del préstamo insertado.");
                }
                int idEnBase = llaves.getInt(1);
                Bitacora.info("Prestamo guardado en la tabla prestamos con id " + idEnBase
                        + " (cliente " + p.getClienteId() + ")");
                return idEnBase;
            }
        } catch (SQLException e) {
            if (MYSQL_LLAVE_FORANEA_INEXISTENTE.contains(e.getErrorCode())) {
                throw new DatoInvalidoException(
                        "No existe un cliente con ID " + p.getClienteId() + " en la base de datos.", e);
            }
            Bitacora.error("No se pudo guardar el prestamo del cliente " + p.getClienteId(), e);
            throw new PersistenciaException(
                    "No se pudo guardar el préstamo en la base de datos: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    /**
     * Reglas que debe cumplir cualquier prestamo, vaya a la lista o a la base.
     * Ademas de las del enunciado (monto y plazo mayores que 0) se revisan los
     * limites que la tabla impone con {@code CHECK}, para que un dato fuera de
     * rango se rechace con un mensaje claro y no con un error de MySQL.
     */
    private static void validar(Prestamo p) throws DatoInvalidoException {
        if (p == null) {
            throw new DatoInvalidoException("El préstamo es obligatorio.");
        }
        if (!esPositivo(p.getMonto())) {
            throw new DatoInvalidoException(
                    "El monto debe ser mayor que 0 (se recibió " + p.getMonto() + ").");
        }
        if (p.getPlazoMeses() <= 0) {
            throw new DatoInvalidoException(
                    "El plazo debe ser mayor que 0 meses (se recibió " + p.getPlazoMeses() + ").");
        }
        if (p.getPlazoMeses() > PLAZO_MAXIMO) {
            throw new DatoInvalidoException("El plazo no puede superar " + PLAZO_MAXIMO
                    + " meses (se recibió " + p.getPlazoMeses() + ").");
        }
        if (!Double.isFinite(p.getInteresMensual())
                || p.getInteresMensual() < 0 || p.getInteresMensual() > INTERES_MAXIMO) {
            throw new DatoInvalidoException("El interés mensual debe estar entre 0 y " + INTERES_MAXIMO
                    + " % (se recibió " + p.getInteresMensual() + ").");
        }
        if (p.getClienteId() <= 0) {
            throw new DatoInvalidoException(
                    "El ID del cliente debe ser mayor que 0 (se recibió " + p.getClienteId() + ").");
        }
    }

    /** Falso para cero, negativos, NaN e infinito. */
    private static boolean esPositivo(double valor) {
        return Double.isFinite(valor) && valor > 0;
    }

    private boolean existe(int id) {
        return prestamos.stream().anyMatch(prestamo -> prestamo.getId() == id);
    }

    /**
     * Las columnas son DECIMAL con dos decimales. {@code BigDecimal.valueOf}
     * parte del texto del double (2.5 da 2.5) y no de su valor binario exacto
     * (2.4999999...), asi que el redondeo no introduce centavos de mas o de menos.
     */
    private static BigDecimal aDecimal(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }
}
