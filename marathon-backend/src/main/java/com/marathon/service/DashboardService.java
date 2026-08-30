package com.marathon.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.marathon.dto.dashboard.DashboardKpisDTO;
import com.marathon.dto.dashboard.EstadoPedidoDTO;
import com.marathon.dto.dashboard.MovimientoResumenDTO;
import com.marathon.dto.dashboard.TopProductoDTO;
import com.marathon.dto.dashboard.VentaDiaDTO;
import com.marathon.repository.DetallePedidoRepository;
import com.marathon.repository.InventarioRepository;
import com.marathon.repository.MovimientoInventarioRepository;
import com.marathon.repository.PedidoRepository;
import com.marathon.repository.ProductoRepository;
import com.marathon.repository.OrdenProduccionRepository;

@Service
public class DashboardService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final ProductoRepository productoRepository;
    private final OrdenProduccionRepository ordenProduccionRepository;

    public DashboardService(PedidoRepository pedidoRepository,
                            DetallePedidoRepository detallePedidoRepository,
                            InventarioRepository inventarioRepository,
                            MovimientoInventarioRepository movimientoInventarioRepository,
                            ProductoRepository productoRepository,
                            OrdenProduccionRepository ordenProduccionRepository) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.inventarioRepository = inventarioRepository;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
        this.productoRepository = productoRepository;
        this.ordenProduccionRepository = ordenProduccionRepository;
    }

    // =====================================================================
    // F94 — Memoria corta para los recuentos del tablero
    // =====================================================================
    // Los KPIs son catorce recuentos sobre tablas de millón y medio de filas.
    // Individualmente ninguno es escandaloso (30–215 ms); sumados son un
    // segundo largo, y el tablero es la primera pantalla despues de entrar.
    //
    // POR QUE UNA MEMORIA CORTA Y NO MAS INDICES. Ya no es un problema de
    // indices: son agregados que tienen que recorrer lo que cuentan. «Cuantos
    // pedidos hay pendientes» no se puede saber sin contarlos.
    //
    // POR QUE ES ACEPTABLE AQUI Y NO EN OTRO SITIO. Un panel de indicadores se
    // mira para hacerse una idea, no para decidir sobre una fila concreta.
    // Que diga 1.482 cuando hace veinte segundos pasaron a 1.483 no cambia
    // ninguna decision. En un listado, en cambio, ver una fila que ya no esta
    // SI importa — y por eso los listados no llevan memoria.
    //
    // Es deliberadamente casera y no una libreria de cache: son veinte lineas,
    // se ve entera, y no anade una dependencia por un panel.
    @org.springframework.beans.factory.annotation.Value("${app.tablero.segundos-memoria:20}")
    private long segundosMemoria;

    private record Recordado<T>(T valor, long instante) {}

    private final java.util.Map<String, Recordado<Object>> memoria =
            new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private <T> T recordar(String clave, java.util.function.Supplier<T> calcular) {
        if (segundosMemoria <= 0) {
            return calcular.get();   // 0 desactiva la memoria; queda el camino de siempre
        }
        long ahora = System.currentTimeMillis();
        Recordado<Object> guardado = memoria.get(clave);
        if (guardado != null && ahora - guardado.instante() < segundosMemoria * 1000) {
            return (T) guardado.valor();
        }
        T fresco = calcular.get();
        memoria.put(clave, new Recordado<>(fresco, ahora));
        return fresco;
    }

    /**
     * F94: los cinco recuentos por estado salen de UNA consulta agrupada.
     *
     * <p>Eran cinco {@code countByEstado(...)} seguidos —cinco recorridos de la
     * tabla de pedidos, 1,5 millones de filas cada uno— para repartir el mismo
     * total en cinco casillas. La consulta agrupada ya existía desde antes:
     * {@code pedidosPorEstado()}, que es la que alimenta el gráfico de al lado.
     * Se reutiliza en lugar de volver a preguntar cinco veces.
     *
     * <p>Un estado sin ningún pedido no aparece en el resultado agrupado, y ahí
     * el cero SÍ es el valor correcto: significa que no hay ninguno.
     */
    public DashboardKpisDTO getKpis() {
        return recordar("kpis", this::calcularKpis);
    }

    private DashboardKpisDTO calcularKpis() {
        DashboardKpisDTO dto = new DashboardKpisDTO();

        java.util.Map<String, Long> porEstado = new java.util.HashMap<>();
        for (com.marathon.dto.dashboard.EstadoPedidoDTO e : pedidoRepository.pedidosPorEstado()) {
            porEstado.put(e.getEstado(), e.getCantidad());
        }
        dto.setPedidosPendientes(porEstado.getOrDefault("pendiente", 0L));
        dto.setPedidosProcesados(porEstado.getOrDefault("procesado", 0L));
        dto.setPedidosEnviados(porEstado.getOrDefault("enviado", 0L));
        dto.setPedidosEntregados(porEstado.getOrDefault("entregado", 0L));
        dto.setPedidosAnulados(porEstado.getOrDefault("anulado", 0L));
        dto.setPedidosHoy(pedidoRepository.contarPedidosHoy());
        dto.setTotalVentasHoy(pedidoRepository.totalVentasHoy());
        dto.setTotalVentasMes(pedidoRepository.totalVentasMes());
        dto.setProductosStockBajo(inventarioRepository.contarProductosStockBajo());
        dto.setPedidosEspecialesActivos(pedidoRepository.countByEsPedidoEspecialTrueAndEstadoNot("anulado"));
        dto.setPedidosPickingPendiente(detallePedidoRepository.contarPedidosPickingPendiente());
        dto.setProductosFabricados(productoRepository.countByOrigen("fabricado"));
        dto.setOrdenesProduccionEnProceso(ordenProduccionRepository.countByEstado("en_proceso"));
        dto.setCostoPromedioProduccionMes(
                ordenProduccionRepository.costoPromedioProduccionDesde(
                        LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        return dto;
    }

    public List<VentaDiaDTO> getVentasPorDia(int dias) {
        int diasClamp = Math.max(1, Math.min(30, dias));
        LocalDateTime desde = LocalDate.now().minusDays(diasClamp - 1L).atStartOfDay();
        return pedidoRepository.ventasPorDia(desde);
    }

    public List<EstadoPedidoDTO> getPedidosPorEstado() {
        return pedidoRepository.pedidosPorEstado();
    }

    /**
     * F94: el agregado no toca producto ni categoría; los nombres se buscan
     * después, solo para los que salen. Ver
     * {@code DetallePedidoRepository.topProductosCrudo}.
     */
    public List<TopProductoDTO> getTopProductos(int limite) {
        int limiteClamp = Math.max(1, Math.min(20, limite));
        return recordar("top-" + limiteClamp, () -> calcularTopProductos(limiteClamp));
    }

    private List<TopProductoDTO> calcularTopProductos(int limiteClamp) {
        List<Object[]> filas = detallePedidoRepository.topProductosCrudo(PageRequest.of(0, limiteClamp));

        List<Integer> ids = filas.stream()
                .map(f -> ((Number) f[0]).intValue())
                .toList();
        // Una consulta para los diez nombres, no una por fila.
        java.util.Map<Integer, com.marathon.model.Producto> porId = new java.util.HashMap<>();
        for (com.marathon.model.Producto p : productoRepository.findAllById(ids)) {
            porId.put(p.getIdProducto(), p);
        }

        List<TopProductoDTO> top = new java.util.ArrayList<>();
        for (Object[] f : filas) {
            Integer id = ((Number) f[0]).intValue();
            com.marathon.model.Producto p = porId.get(id);
            top.add(new TopProductoDTO(
                    id,
                    p != null ? p.getNombre() : null,
                    p != null && p.getCategoria() != null ? p.getCategoria().getNombre() : null,
                    ((Number) f[1]).longValue(),
                    (java.math.BigDecimal) f[2]));
        }
        return top;
    }

    public List<MovimientoResumenDTO> getMovimientosHoy() {
        return movimientoInventarioRepository.movimientosHoy();
    }
}
