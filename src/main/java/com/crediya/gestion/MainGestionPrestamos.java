package com.crediya.gestion;

import com.crediya.excepcion.PersistenciaException;

import java.util.List;

/**
 * Demostracion del gestor de prestamos: registra, lista, filtra por estado y
 * guarda en MySQL.
 *
 * <p>Se ejecuta con {@code ./run.sh gestion}. La parte de MySQL es opcional:
 * si la base no responde, el programa lo explica y termina normalmente,
 * porque todo lo anterior ya funciono en memoria.</p>
 */
public final class MainGestionPrestamos {

    private MainGestionPrestamos() {
        // Clase de arranque: no se instancia.
    }

    public static void main(String[] args) {
        GestorPrestamos gestor = new GestorPrestamos();

        System.out.println("=== REGISTRO DE PRÉSTAMOS ===");
        registrar(gestor, 5, 500000, 2.5, 6, Prestamo.ACTIVO);
        registrar(gestor, 2, 2000000, 3.0, 12, Prestamo.MORA);
        registrar(gestor, 3, 1200000, 2.0, 4, Prestamo.PAGADO);
        registrar(gestor, 1, 0, 2.0, 12, Prestamo.ACTIVO);
        registrar(gestor, 4, 800000, 1.5, 0, Prestamo.ACTIVO);
        registrar(gestor, 4, 800000, 1.5, 6, "Cancelado");

        System.out.println();
        System.out.println("=== LISTADO DE PRÉSTAMOS ===");
        gestor.listarPrestamos();

        mostrarPorEstado(gestor, Prestamo.ACTIVO);
        mostrarPorEstado(gestor, Prestamo.MORA);

        guardarEnBaseDeDatos(gestor);
    }

    private static void registrar(GestorPrestamos gestor, int clienteId, double monto,
                                  double interesMensual, int plazoMeses, String estado) {
        try {
            Prestamo prestamo = new Prestamo(0, clienteId, monto, interesMensual, plazoMeses, estado);
            gestor.registrarPrestamo(prestamo);
            System.out.println("Préstamo registrado correctamente.");
        } catch (DatoInvalidoException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void mostrarPorEstado(GestorPrestamos gestor, String estado) {
        System.out.println();
        System.out.println("=== PRÉSTAMOS EN ESTADO '" + estado + "' ===");
        try {
            List<Prestamo> encontrados = gestor.buscarPorEstado(estado);
            if (encontrados.isEmpty()) {
                System.out.println("No hay préstamos en ese estado.");
            }
            encontrados.forEach(prestamo -> System.out.println(
                    "Préstamo ID " + prestamo.getId() + " - Estado: " + prestamo.getEstado()));
        } catch (DatoInvalidoException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Intenta guardar cada prestamo de la lista. Un prestamo rechazado no
     * detiene a los demas; una falla de conexion si, porque afectaria a todos.
     */
    private static void guardarEnBaseDeDatos(GestorPrestamos gestor) {
        System.out.println();
        System.out.println("=== GUARDADO EN MySQL (tabla prestamos) ===");

        for (Prestamo prestamo : gestor.getPrestamos()) {
            try {
                int idEnBase = gestor.guardarPrestamoEnBD(prestamo);
                System.out.println("Préstamo ID " + prestamo.getId()
                        + " guardado en la base de datos con ID " + idEnBase + ".");
            } catch (DatoInvalidoException e) {
                System.out.println("Préstamo ID " + prestamo.getId() + " no se guardó: " + e.getMessage());
            } catch (PersistenciaException e) {
                System.out.println("Error de base de datos: " + e.getMessage());
                System.out.println("Los préstamos quedaron solo en memoria. "
                        + "El detalle está en datos/bitacora.log");
                return;
            }
        }
    }
}
