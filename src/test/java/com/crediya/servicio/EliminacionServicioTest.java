package com.crediya.servicio;

import com.crediya.config.FabricaRepositorios;
import com.crediya.config.Repositorios;
import com.crediya.excepcion.RecursoNoEncontradoException;
import com.crediya.excepcion.ReglaNegocioException;
import com.crediya.modelo.Cliente;
import com.crediya.modelo.Empleado;
import com.crediya.modelo.EstadoPrestamo;
import com.crediya.modelo.Pago;
import com.crediya.modelo.Prestamo;
import com.crediya.modelo.Rol;
import com.crediya.modelo.TipoInteres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del borrado de datos en los cuatro modulos.
 *
 * <p>Se ejercita sobre el almacen en memoria a proposito: es el que no tiene
 * llaves foraneas, de modo que lo que aqui se verifica es que la regla la pone
 * el servicio y no la base de datos.</p>
 */
@DisplayName("Eliminacion de datos")
class EliminacionServicioTest {

    private Repositorios repositorios;
    private ClienteServicio clienteServicio;
    private EmpleadoServicio empleadoServicio;
    private PrestamoServicio prestamoServicio;
    private PagoServicio pagoServicio;

    private Cliente cliente;
    private Empleado empleado;

    @BeforeEach
    void prepararSistema() {
        repositorios = FabricaRepositorios.crearMemoria();

        empleadoServicio = new EmpleadoServicio(
                repositorios.empleados(), repositorios.prestamos());
        clienteServicio = new ClienteServicio(
                repositorios.clientes(), repositorios.prestamos());
        prestamoServicio = new PrestamoServicio(
                repositorios.prestamos(), clienteServicio, empleadoServicio);
        pagoServicio = new PagoServicio(
                repositorios.pagos(), repositorios.prestamos(), prestamoServicio);

        cliente = clienteServicio.registrar("Pedro Sanchez", "1005432198",
                "pedro@correo.com", "3105558877");
        empleado = empleadoServicio.registrar("Ana Gomez", "1098765432", Rol.ASESOR,
                "ana@crediya.co", new BigDecimal("3200000"));
    }

    /** Total 1.200.000 con interes simple del 2% a 10 cuotas. */
    private Prestamo crearPrestamo() {
        return prestamoServicio.crear(cliente.getId(), empleado.getId(),
                new BigDecimal("1000000"), new BigDecimal("2"), 10, TipoInteres.SIMPLE);
    }

    @Nested
    @DisplayName("Clientes")
    class Clientes {

        @Test
        @DisplayName("Un cliente sin prestamos se elimina")
        void eliminaClienteSinPrestamos() {
            clienteServicio.eliminar(cliente.getId());

            assertEquals(0, repositorios.clientes().contar());
            assertThrows(RecursoNoEncontradoException.class,
                    () -> clienteServicio.buscarPorId(cliente.getId()));
        }

        @Test
        @DisplayName("Un cliente con prestamos no se puede eliminar")
        void bloqueaClienteConPrestamos() {
            crearPrestamo();

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> clienteServicio.eliminar(cliente.getId()));

            assertTrue(error.getMessage().contains("prestamo"));
            assertEquals(1, repositorios.clientes().contar());
        }

        @Test
        @DisplayName("Eliminar un cliente que no existe falla con mensaje claro")
        void clienteInexistente() {
            assertThrows(RecursoNoEncontradoException.class, () -> clienteServicio.eliminar(999));
        }
    }

    @Nested
    @DisplayName("Empleados")
    class Empleados {

        @Test
        @DisplayName("Un empleado sin prestamos a su nombre se elimina")
        void eliminaEmpleadoSinPrestamos() {
            empleadoServicio.eliminar(empleado.getId());

            assertEquals(0, repositorios.empleados().contar());
        }

        @Test
        @DisplayName("Un empleado con prestamos colocados no se puede eliminar")
        void bloqueaEmpleadoConPrestamos() {
            crearPrestamo();

            assertThrows(ReglaNegocioException.class,
                    () -> empleadoServicio.eliminar(empleado.getId()));
            assertEquals(1, repositorios.empleados().contar());
        }
    }

    @Nested
    @DisplayName("Prestamos")
    class Prestamos {

        @Test
        @DisplayName("Un prestamo sin abonos se elimina")
        void eliminaPrestamoSinAbonos() {
            Prestamo prestamo = crearPrestamo();

            prestamoServicio.eliminar(prestamo.getId());

            assertEquals(0, repositorios.prestamos().contar());
        }

        @Test
        @DisplayName("Un prestamo con abonos no se puede eliminar")
        void bloqueaPrestamoConAbonos() {
            Prestamo prestamo = crearPrestamo();
            pagoServicio.registrarAbono(prestamo.getId(), new BigDecimal("120000"));

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> prestamoServicio.eliminar(prestamo.getId()));

            assertTrue(error.getMessage().contains("abonos"));
            assertEquals(1, repositorios.prestamos().contar());
        }

        @Test
        @DisplayName("Al eliminar el prestamo el cliente queda libre para borrarse")
        void trasEliminarPrestamoSePuedeBorrarElCliente() {
            Prestamo prestamo = crearPrestamo();

            prestamoServicio.eliminar(prestamo.getId());
            clienteServicio.eliminar(cliente.getId());

            assertEquals(0, repositorios.clientes().contar());
        }
    }

    @Nested
    @DisplayName("Abonos")
    class Abonos {

        @Test
        @DisplayName("Al eliminar un abono el saldo vuelve a subir")
        void eliminarAbonoDevuelveElSaldo() {
            Prestamo prestamo = crearPrestamo();
            Pago pago = pagoServicio.registrarAbono(prestamo.getId(), new BigDecimal("200000"));

            Prestamo actualizado = pagoServicio.eliminar(pago.getId());

            assertEquals(0, repositorios.pagos().contar());
            assertEquals(0, new BigDecimal("1200000.00")
                    .compareTo(actualizado.getSaldoPendiente()));
        }

        @Test
        @DisplayName("Un prestamo ya cancelado vuelve a ACTIVO y admite abonos de nuevo")
        void eliminarAbonoReabreElPrestamo() {
            Prestamo prestamo = crearPrestamo();
            Pago pago = pagoServicio.registrarAbono(prestamo.getId(), new BigDecimal("1200000"));

            // Queda saldado: el estado pasa a PAGADO.
            assertEquals(EstadoPrestamo.PAGADO,
                    repositorios.prestamos().buscarPorId(prestamo.getId()).orElseThrow().getEstado());

            Prestamo reabierto = pagoServicio.eliminar(pago.getId());

            // Sin reabrirlo quedaria debiendo plata y rechazando cualquier abono.
            assertEquals(EstadoPrestamo.ACTIVO, reabierto.getEstado());
            assertEquals(0, new BigDecimal("1200000.00").compareTo(reabierto.getSaldoPendiente()));

            Pago nuevo = pagoServicio.registrarAbono(prestamo.getId(), new BigDecimal("120000"));
            assertEquals(prestamo.getId(), nuevo.getPrestamoId());
        }

        @Test
        @DisplayName("Eliminar un abono que no existe falla con mensaje claro")
        void abonoInexistente() {
            assertThrows(RecursoNoEncontradoException.class, () -> pagoServicio.eliminar(999));
        }
    }
}
