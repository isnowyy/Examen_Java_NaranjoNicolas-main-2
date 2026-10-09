package com.crediya.gestion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gestor de prestamos en memoria.
 *
 * <p>Ninguna prueba necesita MySQL: las de {@code guardarPrestamoEnBD} solo
 * cubren los rechazos, que ocurren antes de abrir la conexion.</p>
 */
@DisplayName("Gestor de prestamos")
class GestorPrestamosTest {

    /** Linea del "Ejemplo de ejecucion esperada" del enunciado. */
    private static final String LINEA_DEL_ENUNCIADO =
            "ID: 1 - Cliente: 5 - Monto: 500000.0 - Interés: 2.5% - Plazo: 6 meses - Total: 575000.0";

    private GestorPrestamos gestor;

    @BeforeEach
    void prepararGestor() {
        gestor = new GestorPrestamos();
    }

    private static Prestamo prestamo(int clienteId, String estado) throws DatoInvalidoException {
        return new Prestamo(0, clienteId, 500000, 2.5, 6, estado);
    }

    @Nested
    @DisplayName("registrarPrestamo")
    class Registro {

        @Test
        @DisplayName("Asigna identificadores consecutivos a los prestamos sin ID")
        void asignaIdentificadores() throws DatoInvalidoException {
            Prestamo primero = new Prestamo(5, 500000, 2.5, 6);
            Prestamo segundo = new Prestamo(2, 2000000, 3, 12);

            gestor.registrarPrestamo(primero);
            gestor.registrarPrestamo(segundo);

            assertEquals(1, primero.getId());
            assertEquals(2, segundo.getId());
            assertEquals(List.of(primero, segundo), gestor.getPrestamos());
        }

        @Test
        @DisplayName("Respeta un ID dado y sigue numerando despues de el")
        void respetaElIdDado() throws DatoInvalidoException {
            gestor.registrarPrestamo(new Prestamo(10, 5, 500000, 2.5, 6, Prestamo.ACTIVO));
            Prestamo siguiente = new Prestamo(2, 2000000, 3, 12);

            gestor.registrarPrestamo(siguiente);

            assertEquals(11, siguiente.getId());
        }

        @Test
        @DisplayName("Rechaza un ID que ya esta registrado")
        void rechazaIdRepetido() throws DatoInvalidoException {
            gestor.registrarPrestamo(new Prestamo(1, 5, 500000, 2.5, 6, Prestamo.ACTIVO));

            assertThrows(DatoInvalidoException.class,
                    () -> gestor.registrarPrestamo(new Prestamo(1, 2, 300000, 2, 3, Prestamo.ACTIVO)));
            assertEquals(1, gestor.getPrestamos().size());
        }

        @ParameterizedTest(name = "monto {0}")
        @ValueSource(doubles = {0, -100000, Double.NaN, Double.POSITIVE_INFINITY})
        @DisplayName("Rechaza un monto que no es mayor que 0")
        void rechazaMontoInvalido(double monto) {
            DatoInvalidoException error = assertThrows(DatoInvalidoException.class,
                    () -> gestor.registrarPrestamo(new Prestamo(5, monto, 2.5, 6)));

            assertTrue(error.getMessage().contains("monto"), error.getMessage());
            assertTrue(gestor.getPrestamos().isEmpty(), "Un prestamo rechazado no entra a la lista");
        }

        @ParameterizedTest(name = "plazo {0}")
        @ValueSource(ints = {0, -6, 121})
        @DisplayName("Rechaza un plazo que no esta entre 1 y 120 meses")
        void rechazaPlazoInvalido(int plazo) {
            DatoInvalidoException error = assertThrows(DatoInvalidoException.class,
                    () -> gestor.registrarPrestamo(new Prestamo(5, 500000, 2.5, plazo)));

            assertTrue(error.getMessage().contains("plazo"), error.getMessage());
            assertTrue(gestor.getPrestamos().isEmpty());
        }

        @ParameterizedTest(name = "interes {0}")
        @ValueSource(doubles = {-1, 100.5})
        @DisplayName("Rechaza un interes fuera del rango 0 a 100")
        void rechazaInteresInvalido(double interes) {
            assertThrows(DatoInvalidoException.class,
                    () -> gestor.registrarPrestamo(new Prestamo(5, 500000, interes, 6)));
        }

        @Test
        @DisplayName("Rechaza un prestamo sin cliente")
        void rechazaSinCliente() {
            assertThrows(DatoInvalidoException.class,
                    () -> gestor.registrarPrestamo(new Prestamo(0, 500000, 2.5, 6)));
        }

        @Test
        @DisplayName("Rechaza un prestamo nulo")
        void rechazaNulo() {
            assertThrows(DatoInvalidoException.class, () -> gestor.registrarPrestamo(null));
        }

        @Test
        @DisplayName("Un prestamo rechazado no recibe identificador")
        void rechazadoNoSeModifica() {
            Prestamo invalido = new Prestamo(5, 0, 2.5, 6);

            assertThrows(DatoInvalidoException.class, () -> gestor.registrarPrestamo(invalido));
            assertEquals(0, invalido.getId());
        }

        @Test
        @DisplayName("La lista no se puede modificar desde afuera")
        void listaDeSoloLectura() {
            assertThrows(UnsupportedOperationException.class,
                    () -> gestor.getPrestamos().add(new Prestamo(5, 500000, 2.5, 6)));
        }
    }

    @Nested
    @DisplayName("listarPrestamos")
    class Listado {

        private final PrintStream salidaOriginal = System.out;
        private final ByteArrayOutputStream capturada = new ByteArrayOutputStream();

        @BeforeEach
        void capturarConsola() {
            System.setOut(new PrintStream(capturada, true, StandardCharsets.UTF_8));
        }

        @AfterEach
        void restaurarConsola() {
            System.setOut(salidaOriginal);
        }

        private String salida() {
            return capturada.toString(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("Muestra cada prestamo con su total")
        void muestraCadaPrestamo() throws DatoInvalidoException {
            gestor.registrarPrestamo(new Prestamo(5, 500000, 2.5, 6));
            gestor.registrarPrestamo(new Prestamo(2, 2000000, 3, 12));

            gestor.listarPrestamos();

            List<String> lineas = salida().lines().toList();
            assertEquals(2, lineas.size());
            assertEquals(LINEA_DEL_ENUNCIADO, lineas.get(0));
            assertTrue(lineas.get(1).endsWith("Total: 2720000.0"), lineas.get(1));
        }

        @Test
        @DisplayName("Avisa cuando no hay prestamos")
        void avisaSiEstaVacio() {
            gestor.listarPrestamos();

            assertEquals("No hay préstamos registrados.", salida().strip());
        }
    }

    @Nested
    @DisplayName("buscarPorEstado")
    class BusquedaPorEstado {

        @BeforeEach
        void registrarUnoDeCada() throws DatoInvalidoException {
            gestor.registrarPrestamo(prestamo(5, Prestamo.ACTIVO));
            gestor.registrarPrestamo(prestamo(2, Prestamo.MORA));
            gestor.registrarPrestamo(prestamo(3, Prestamo.PAGADO));
            gestor.registrarPrestamo(prestamo(4, Prestamo.ACTIVO));
        }

        @Test
        @DisplayName("Devuelve solo los prestamos del estado pedido, en orden de registro")
        void filtraPorEstado() throws DatoInvalidoException {
            List<Prestamo> activos = gestor.buscarPorEstado("Activo");

            assertEquals(List.of(1, 4), activos.stream().map(Prestamo::getId).toList());
        }

        @Test
        @DisplayName("No distingue mayusculas en el estado buscado")
        void ignoraMayusculas() throws DatoInvalidoException {
            assertEquals(1, gestor.buscarPorEstado("MORA").size());
        }

        @Test
        @DisplayName("Devuelve una lista vacia si ningun prestamo tiene ese estado")
        void listaVaciaSinCoincidencias() throws DatoInvalidoException {
            GestorPrestamos vacio = new GestorPrestamos();

            assertTrue(vacio.buscarPorEstado("Pagado").isEmpty());
        }

        @Test
        @DisplayName("Rechaza un estado que no existe en lugar de devolver una lista vacia")
        void rechazaEstadoDesconocido() {
            assertThrows(DatoInvalidoException.class, () -> gestor.buscarPorEstado("Cancelado"));
        }
    }

    @Nested
    @DisplayName("guardarPrestamoEnBD")
    class GuardadoEnBaseDeDatos {

        @Test
        @DisplayName("Rechaza un prestamo Pagado: no tiene abonos que lo respalden")
        void rechazaPagado() throws DatoInvalidoException {
            DatoInvalidoException error = assertThrows(DatoInvalidoException.class,
                    () -> gestor.guardarPrestamoEnBD(prestamo(3, Prestamo.PAGADO)));

            assertTrue(error.getMessage().contains("Pagado"), error.getMessage());
        }

        @Test
        @DisplayName("Aplica las mismas validaciones que registrarPrestamo")
        void validaAntesDeConectar() {
            assertThrows(DatoInvalidoException.class,
                    () -> gestor.guardarPrestamoEnBD(new Prestamo(5, 0, 2.5, 6)));
            assertThrows(DatoInvalidoException.class,
                    () -> gestor.guardarPrestamoEnBD(new Prestamo(5, 500000, 2.5, 0)));
        }
    }
}
