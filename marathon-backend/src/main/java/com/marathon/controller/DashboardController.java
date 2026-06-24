package com.marathon.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.dashboard.DashboardKpisDTO;
import com.marathon.dto.dashboard.EstadoPedidoDTO;
import com.marathon.dto.dashboard.MovimientoResumenDTO;
import com.marathon.dto.dashboard.TopProductoDTO;
import com.marathon.dto.dashboard.VentaDiaDTO;
import com.marathon.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/kpis")
    public ResponseEntity<DashboardKpisDTO> getKpis() {
        return ResponseEntity.ok(dashboardService.getKpis());
    }

    @GetMapping("/ventas-por-dia")
    public ResponseEntity<List<VentaDiaDTO>> getVentasPorDia(
            @RequestParam(defaultValue = "7") int dias) {
        return ResponseEntity.ok(dashboardService.getVentasPorDia(dias));
    }

    @GetMapping("/pedidos-por-estado")
    public ResponseEntity<List<EstadoPedidoDTO>> getPedidosPorEstado() {
        return ResponseEntity.ok(dashboardService.getPedidosPorEstado());
    }

    @GetMapping("/top-productos")
    public ResponseEntity<List<TopProductoDTO>> getTopProductos(
            @RequestParam(defaultValue = "5") int limite) {
        return ResponseEntity.ok(dashboardService.getTopProductos(limite));
    }

    @GetMapping("/movimientos-hoy")
    public ResponseEntity<List<MovimientoResumenDTO>> getMovimientosHoy() {
        return ResponseEntity.ok(dashboardService.getMovimientosHoy());
    }
}
