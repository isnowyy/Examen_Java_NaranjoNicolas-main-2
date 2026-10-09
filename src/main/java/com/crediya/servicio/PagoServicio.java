package com.crediya.servicio;

import com.crediya.excepcion.RecursoNoEncontradoException;
import com.crediya.modelo.EstadoPrestamo;
import com.crediya.modelo.Pago;
import com.crediya.modelo.Prestamo;
import com.crediya.repositorio.PagoRepositorio;
import com.crediya.repositorio.PrestamoRepositorio;
import com.crediya.util.Bitacora;
import com.crediya.util.Dinero;
import com.crediya.util.Validaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Registro de abonos y consulta del historico.
 *
 * <p>Quien decide si un abono es valido es el propio {@link Prestamo}, a traves
 * de {@link Prestamo#registrarPago(Pago)}. Este servicio se limita a orquestar:
 * trae el prestamo, le pasa el abono y persiste el resultado.</p>
 *
 * <p>Alcance conocido: el abono y la actualizacion del prestamo son dos
 * escrituras separadas y no van dentro de una transaccion. Para una aplicacion
 * de consola de un solo usuario el riesgo es despreciable; en un sistema
 * multiusuario habria que envolverlas en una unidad de trabajo. Se deja
 * documentado en lugar de fingir que el problema no existe.</p>
 */
public class PagoServicio {

    private final PagoRepositorio repositorio;
    private final PrestamoRepositorio prestamoRepositorio;
    private final PrestamoServicio prestamoServicio;

    public PagoServicio(PagoRepositorio repositorio,
                        PrestamoRepositorio prestamoRepositorio,
                        PrestamoServicio prestamoServicio) {
        this.repositorio = Validaciones.noNulo(repositorio, "repositorio de pagos");
        this.prestamoRepositorio = Validaciones.noNulo(prestamoRepositorio, "repositorio de prestamos");
        this.prestamoServicio = Validaciones.noNulo(prestamoServicio, "servicio de prestamos");
    }

    /** Abono de hoy. */
    public Pago registrarAbono(int prestamoId, BigDecimal monto) {
        return registrarAbono(prestamoId, monto, LocalDate.now());
    }

    /**
     * Aplica un abono al prestamo y actualiza el saldo.
     *
     * @return el pago ya guardado, con su identificador
     */
    public Pago registrarAbono(int prestamoId, BigDecimal monto, LocalDate fecha) {
        Prestamo prestamo = prestamoServicio.buscarPorId(prestamoId);

        Pago pago = new Pago(null, prestamo.getId(), fecha, monto);
        prestamo.registrarPago(pago);

        Pago guardado = repositorio.guardar(pago);
        prestamoRepositorio.guardar(prestamo);

        Bitacora.info(String.format("Abono de %s aplicado al prestamo #%d; saldo %s",
                Dinero.formatear(guardado.getMonto()), prestamoId,
                Dinero.formatear(prestamo.getSaldoPendiente())));

        if (prestamo.getEstado() == EstadoPrestamo.PAGADO) {
            Bitacora.info("El prestamo #" + prestamoId + " quedo cancelado en su totalidad.");
        }
        return guardado;
    }

    /** Historico de un prestamo, del abono mas antiguo al mas reciente. */
    public List<Pago> historicoDe(int prestamoId) {
        Prestamo prestamo = prestamoServicio.buscarPorId(prestamoId);
        return repositorio.buscarPorPrestamo(prestamo.getId());
    }

    public List<Pago> listar() {
        return repositorio.listar().stream()
                .sorted(Comparator.comparing(Pago::getFechaPago).reversed())
                .toList();
    }

    /** Total recaudado por la empresa. */
    public BigDecimal totalRecaudado() {
        return Dinero.normalizar(repositorio.listar().stream()
                .map(Pago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * Recaudo agrupado por mes, en orden cronologico.
     * {@code TreeMap} porque {@link YearMonth} ya sabe ordenarse solo.
     */
    public Map<YearMonth, BigDecimal> recaudoPorMes() {
        return repositorio.listar().stream()
                .collect(Collectors.groupingBy(
                        pago -> YearMonth.from(pago.getFechaPago()),
                        TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Pago::getMonto, BigDecimal::add)));
    }

    /**
     * Elimina un abono y devuelve el prestamo al saldo que le corresponde.
     *
     * <p>Reabrir el prestamo no es un detalle opcional. El saldo es derivado
     * (se calcula restando los abonos), pero el estado es un campo guardado que
     * {@link Prestamo#registrarPago} pone en {@code PAGADO} al llegar a cero.
     * Si se borra un abono de un prestamo cancelado y el estado no se corrige,
     * queda un credito que debe plata y que ademas rechaza cualquier abono
     * nuevo, porque lo primero que valida {@code registrarPago} es que el
     * prestamo no este marcado como pagado.</p>
     *
     * <p>El historico se recarga desde el almacen en lugar de quitar el pago de
     * la lista en memoria: asi el objeto queda igual sin importar si el almacen
     * reconstruye el prestamo al leerlo (archivo y JDBC) o conserva la misma
     * instancia (memoria).</p>
     *
     * @return el prestamo ya actualizado
     * @throws RecursoNoEncontradoException si el pago no existe
     */
    public Prestamo eliminar(int pagoId) {
        Pago pago = repositorio.buscarPorId(pagoId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Pago", pagoId));

        Prestamo prestamo = prestamoServicio.buscarPorId(pago.getPrestamoId());

        repositorio.eliminar(pagoId);
        prestamo.cargarHistorico(repositorio.buscarPorPrestamo(prestamo.getId()));

        if (prestamo.getEstado() == EstadoPrestamo.PAGADO
                && prestamo.getSaldoPendiente().signum() > 0) {
            prestamo.setEstado(EstadoPrestamo.ACTIVO);
            Bitacora.advertencia("El prestamo #" + prestamo.getId()
                    + " vuelve a estado ACTIVO tras eliminar un abono.");
        }

        prestamoRepositorio.guardar(prestamo);

        Bitacora.info(String.format("Abono #%d de %s eliminado del prestamo #%d; saldo %s",
                pagoId, Dinero.formatear(pago.getMonto()), prestamo.getId(),
                Dinero.formatear(prestamo.getSaldoPendiente())));
        return prestamo;
    }
}
