package com.crediya.gestion;

import com.crediya.modelo.interes.InteresSimple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prestamo del gestor en memoria: calculo del total, formato de
 * {@code toString()} y control del estado.
 */
@DisplayName("Prestamo del gestor")
class PrestamoTest {

    private static final double CENTAVO = 0.001;

    @Test
    @DisplayName("Calcula el total del ejemplo del enunciado: 500.000 al 2,5 % por 6 meses da 575.000")
    void calculaElTotalDelEnunciado() {
        Prestamo prestamo = new Prestamo(5, 500000, 2.5, 6);

        assertEquals(575000.0, prestamo.calcularTotalAPagar(), CENTAVO);
    }

    @Test
    @DisplayName("Con interes 0 el total es el mismo monto")
    void sinInteresDevuelveElMonto() {
        assertEquals(800000.0, new Prestamo(1, 800000, 0, 12).calcularTotalAPagar(), CENTAVO);
    }

    @Test
    @DisplayName("Da el mismo total que el interes simple del resto del sistema")
    void coincideConInteresSimple() {
        Prestamo prestamo = new Prestamo(1, 1000000, 2, 10);

        BigDecimal delSistema = new InteresSimple()
                .calcular(new BigDecimal("1000000"), new BigDecimal("2"), 10)
                .montoTotal();

        assertEquals(delSistema.doubleValue(), prestamo.calcularTotalAPagar(), CENTAVO);
    }

    @Test
    @DisplayName("toString() produce la linea del ejemplo de ejecucion")
    void toStringComoElEnunciado() throws DatoInvalidoException {
        Prestamo prestamo = new Prestamo(1, 5, 500000, 2.5, 6, Prestamo.ACTIVO);

        assertEquals(
                "ID: 1 - Cliente: 5 - Monto: 500000.0 - Interés: 2.5% - Plazo: 6 meses - Total: 575000.0",
                prestamo.toString());
    }

    @Test
    @DisplayName("toString() no usa notacion cientifica con montos grandes")
    void toStringSinNotacionCientifica() {
        String texto = new Prestamo(1, 12000000, 1.5, 36).toString();

        assertTrue(texto.contains("Monto: 12000000.0"), texto);
        assertTrue(texto.contains("Total: 18480000.0"), texto);
    }

    @Test
    @DisplayName("Un prestamo nuevo empieza Activo y sin identificador")
    void nuevoEmpiezaActivo() {
        Prestamo prestamo = new Prestamo(5, 500000, 2.5, 6);

        assertEquals("Activo", prestamo.getEstado());
        assertEquals(0, prestamo.getId());
    }

    @Test
    @DisplayName("El estado no distingue mayusculas y se guarda con la forma del enunciado")
    void normalizaElEstado() throws DatoInvalidoException {
        Prestamo prestamo = new Prestamo(5, 500000, 2.5, 6);

        prestamo.setEstado(" mora ");

        assertEquals("Mora", prestamo.getEstado());
    }

    @ParameterizedTest(name = "rechaza el estado \"{0}\"")
    @ValueSource(strings = {"Cancelado", "Pendiente", "", "   "})
    @DisplayName("Rechaza estados que no son Activo, Pagado ni Mora")
    void rechazaEstadosInvalidos(String estado) {
        Prestamo prestamo = new Prestamo(5, 500000, 2.5, 6);

        assertThrows(DatoInvalidoException.class, () -> prestamo.setEstado(estado));
        assertEquals("Activo", prestamo.getEstado(), "Un estado rechazado no cambia el anterior");
    }

    @Test
    @DisplayName("El constructor completo tambien valida el estado")
    void constructorValidaElEstado() {
        DatoInvalidoException error = assertThrows(DatoInvalidoException.class,
                () -> new Prestamo(1, 5, 500000, 2.5, 6, null));

        assertTrue(error.getMessage().contains("Activo, Pagado, Mora"), error.getMessage());
    }
}
