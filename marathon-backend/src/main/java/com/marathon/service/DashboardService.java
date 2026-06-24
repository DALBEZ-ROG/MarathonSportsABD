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

@Service
public class DashboardService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;

    public DashboardService(PedidoRepository pedidoRepository,
                            DetallePedidoRepository detallePedidoRepository,
                            InventarioRepository inventarioRepository,
                            MovimientoInventarioRepository movimientoInventarioRepository) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.inventarioRepository = inventarioRepository;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
    }

    public DashboardKpisDTO getKpis() {
        DashboardKpisDTO dto = new DashboardKpisDTO();
        dto.setPedidosPendientes(pedidoRepository.countByEstado("pendiente"));
        dto.setPedidosProcesados(pedidoRepository.countByEstado("procesado"));
        dto.setPedidosEnviados(pedidoRepository.countByEstado("enviado"));
        dto.setPedidosEntregados(pedidoRepository.countByEstado("entregado"));
        dto.setPedidosAnulados(pedidoRepository.countByEstado("anulado"));
        dto.setPedidosHoy(pedidoRepository.contarPedidosHoy());
        dto.setTotalVentasHoy(pedidoRepository.totalVentasHoy());
        dto.setTotalVentasMes(pedidoRepository.totalVentasMes());
        dto.setProductosStockBajo(inventarioRepository.contarProductosStockBajo());
        dto.setPedidosEspecialesActivos(pedidoRepository.countByEsPedidoEspecialTrueAndEstadoNot("anulado"));
        dto.setPedidosPickingPendiente(detallePedidoRepository.contarPedidosPickingPendiente());
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

    public List<TopProductoDTO> getTopProductos(int limite) {
        int limiteClamp = Math.max(1, Math.min(20, limite));
        return detallePedidoRepository.topProductos(PageRequest.of(0, limiteClamp));
    }

    public List<MovimientoResumenDTO> getMovimientosHoy() {
        return movimientoInventarioRepository.movimientosHoy();
    }
}
