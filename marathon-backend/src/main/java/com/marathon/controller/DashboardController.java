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
import com.marathon.dto.reporte.ResumenManufacturaDTO;
import com.marathon.service.DashboardService;
import com.marathon.service.ReporteManufacturaService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ReporteManufacturaService reporteManufacturaService;

    public DashboardController(DashboardService dashboardService,
                               ReporteManufacturaService reporteManufacturaService) {
        this.dashboardService = dashboardService;
        this.reporteManufacturaService = reporteManufacturaService;
    }

    /** F30 — Resumen del dashboard de manufactura. */
    @GetMapping("/manufactura")
    public ResponseEntity<ResumenManufacturaDTO> getResumenManufactura() {
        return ResponseEntity.ok(reporteManufacturaService.resumenManufactura());
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
