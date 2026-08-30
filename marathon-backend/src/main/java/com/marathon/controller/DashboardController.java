package com.marathon.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.dashboard.AnaliticaDTO;
import com.marathon.dto.dashboard.DashboardKpisDTO;
import com.marathon.dto.dashboard.DashboardResumenDTO;
import com.marathon.dto.dashboard.EstadoPedidoDTO;
import com.marathon.dto.dashboard.MovimientoResumenDTO;
import com.marathon.dto.dashboard.TopProductoDTO;
import com.marathon.dto.dashboard.VentaDiaDTO;
import com.marathon.dto.reporte.ResumenManufacturaDTO;
import com.marathon.service.DashboardResumenService;
import com.marathon.service.AnaliticaService;
import com.marathon.service.DashboardService;
import com.marathon.service.ReporteManufacturaService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardResumenService dashboardResumenService;
    private final ReporteManufacturaService reporteManufacturaService;
    private final AnaliticaService analiticaService;

    public DashboardController(DashboardService dashboardService,
                               DashboardResumenService dashboardResumenService,
                               ReporteManufacturaService reporteManufacturaService,
                               AnaliticaService analiticaService) {
        this.dashboardService = dashboardService;
        this.dashboardResumenService = dashboardResumenService;
        this.reporteManufacturaService = reporteManufacturaService;
        this.analiticaService = analiticaService;
    }

    /**
     * D1 — el tablero del rol que hace la peticion.
     *
     * <p>No lleva parametro de rol a proposito. Si el rol viajara en la URL,
     * cualquiera podria pedir el tablero de otro con solo cambiarla; aqui sale
     * del token, que es lo unico que el usuario no puede reescribir.
     *
     * <p>Devuelve siempre 200 aunque alguna cifra falle: los indicadores que no
     * se pudieron calcular vienen en estado {@code error} con su motivo. Un 500
     * dejaria la pantalla en blanco por una sola consulta rota.
     *
     * @param periodo {@code 7d}, {@code 30d} (por defecto) o {@code 90d}
     */
    @GetMapping("/resumen")
    public ResponseEntity<DashboardResumenDTO> getResumen(
            @RequestParam(name = "periodo", defaultValue = "30d") String periodo) {
        return ResponseEntity.ok(dashboardResumenService.resumen(periodo));
    }

    /** F30 — Resumen del dashboard de manufactura. */
    @GetMapping("/manufactura")
    @PreAuthorize("hasAuthority('produccion:ver')")
    public ResponseEntity<ResumenManufacturaDTO> getResumenManufactura() {
        return ResponseEntity.ok(reporteManufacturaService.resumenManufactura());
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAuthority('dashboard:ver')")
    public ResponseEntity<DashboardKpisDTO> getKpis() {
        return ResponseEntity.ok(dashboardService.getKpis());
    }

    @GetMapping("/ventas-por-dia")
    @PreAuthorize("hasAuthority('dashboard:ver')")
    public ResponseEntity<List<VentaDiaDTO>> getVentasPorDia(
            @RequestParam(name = "dias", defaultValue = "7") int dias) {
        return ResponseEntity.ok(dashboardService.getVentasPorDia(dias));
    }

    @GetMapping("/pedidos-por-estado")
    @PreAuthorize("hasAuthority('dashboard:ver')")
    public ResponseEntity<List<EstadoPedidoDTO>> getPedidosPorEstado() {
        return ResponseEntity.ok(dashboardService.getPedidosPorEstado());
    }

    @GetMapping("/top-productos")
    @PreAuthorize("hasAuthority('dashboard:ver')")
    public ResponseEntity<List<TopProductoDTO>> getTopProductos(
            @RequestParam(name = "limite", defaultValue = "5") int limite) {
        return ResponseEntity.ok(dashboardService.getTopProductos(limite));
    }

    /**
     * F80 — el análisis del negocio: qué se vende, quién compra y dónde.
     *
     * <p>Va con {@code dashboard:ver}, que ya tienen exactamente los dos roles a
     * los que esto les sirve —Administrador y Supervisor E-Commerce—, así que no
     * hace falta un permiso nuevo para el mismo público.
     *
     * @param periodo {@code 30d} (por defecto), {@code 90d}, {@code 12m} o {@code todo}
     */
    @GetMapping("/analitica")
    @PreAuthorize("hasAuthority('dashboard:ver')")
    public ResponseEntity<AnaliticaDTO> getAnalitica(
            @RequestParam(name = "periodo", defaultValue = "30d") String periodo) {
        return ResponseEntity.ok(analiticaService.analitica(periodo));
    }

    @GetMapping("/movimientos-hoy")
    @PreAuthorize("hasAuthority('dashboard:ver')")
    public ResponseEntity<List<MovimientoResumenDTO>> getMovimientosHoy() {
        return ResponseEntity.ok(dashboardService.getMovimientosHoy());
    }
}
