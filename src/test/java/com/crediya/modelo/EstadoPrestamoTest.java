package com.crediya.modelo;

import com.crediya.excepcion.ValidacionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Vocabulario de la columna {@code estado}: el que se guarda y el que se acepta al leer.
 */
@DisplayName("Estado del prestamo")
class EstadoPrestamoTest {

    @ParameterizedTest(name = "\"{0}\" se lee como {1}")
    @CsvSource({
            "Activo, ACTIVO",
            "ACTIVO, ACTIVO",
            "' pagado ', PAGADO",
            "Mora, MORA",
            "PENDIENTE, ACTIVO"
    })
    @DisplayName("Lee el estado sin distinguir mayusculas, incluido el nombre anterior de ACTIVO")
    void leeElTextoGuardado(String texto, EstadoPrestamo esperado) {
        assertEquals(esperado, EstadoPrestamo.desde(texto));
    }

    @Test
    @DisplayName("Se guarda con los valores del enunciado")
    void seGuardaConLosValoresDelEnunciado() {
        assertEquals("Activo, Pagado, Mora", EstadoPrestamo.opciones());
    }

    @Test
    @DisplayName("Rechaza un estado desconocido")
    void rechazaDesconocido() {
        assertThrows(ValidacionException.class, () -> EstadoPrestamo.desde("Cancelado"));
    }
}
