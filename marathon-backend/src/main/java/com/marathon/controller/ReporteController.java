package com.marathon.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.reporte.FiltroReporteDTO;
import com.marathon.dto.reporte.ReporteCostosProduccionItemDTO;
import com.marathon.dto.reporte.ReporteMovimientosItemDTO;
import com.marathon.dto.reporte.ReportePedidosItemDTO;
import com.marathon.dto.reporte.ReporteVentasProductoItemDTO;
import com.marathon.model.Usuario;
import com.marathon.service.ExcelService;
import com.marathon.service.PdfReporteService;
import com.marathon.service.ReporteService;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private static final String EXCEL_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FECHA_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReporteService reporteService;
    private final ExcelService excelService;
    private final PdfReporteService pdfReporteService;

    public ReporteController(ReporteService reporteService, ExcelService excelService,
                             PdfReporteService pdfReporteService) {
        this.reporteService = reporteService;
        this.excelService = excelService;
        this.pdfReporteService = pdfReporteService;
    }

    // ===================== PEDIDOS =====================
    @PostMapping("/pedidos/preview")
    @PreAuthorize("hasAuthority('reportes:ver')")
    public ResponseEntity<List<ReportePedidosItemDTO>> previewPedidos(@RequestBody FiltroReporteDTO filtro) {
        return ResponseEntity.ok(reporteService.generarReportePedidos(filtro));
    }

    @PostMapping("/pedidos/excel")
    @PreAuthorize("hasAuthority('reportes:exportar')")
    public ResponseEntity<byte[]> excelPedidos(@RequestBody FiltroReporteDTO filtro) {
        List<ReportePedidosItemDTO> datos = reporteService.generarReportePedidos(filtro);
        byte[] archivo = excelService.exportarPedidosExcel(datos, filtro);
        return respuesta(archivo, EXCEL_MIME, "reporte-pedidos-" + hoy() + ".xlsx");
    }

    @PostMapping("/pedidos/pdf")
    @PreAuthorize("hasAuthority('reportes:exportar')")
    public ResponseEntity<byte[]> pdfPedidos(@RequestBody FiltroReporteDTO filtro) {
        List<ReportePedidosItemDTO> datos = reporteService.generarReportePedidos(filtro);
        byte[] archivo = pdfReporteService.exportarPedidosPDF(datos, filtro, nombreUsuario());
        return respuesta(archivo, MediaType.APPLICATION_PDF_VALUE, "reporte-pedidos-" + hoy() + ".pdf");
    }

    // ===================== VENTAS POR PRODUCTO =====================
    @PostMapping("/ventas-producto/preview")
    @PreAuthorize("hasAuthority('reportes:ver')")
    public ResponseEntity<List<ReporteVentasProductoItemDTO>> previewVentas(@RequestBody FiltroReporteDTO filtro) {
        return ResponseEntity.ok(reporteService.generarReporteVentasProducto(filtro));
    }

    @PostMapping("/ventas-producto/excel")
    @PreAuthorize("hasAuthority('reportes:exportar')")
    public ResponseEntity<byte[]> excelVentas(@RequestBody FiltroReporteDTO filtro) {
        List<ReporteVentasProductoItemDTO> datos = reporteService.generarReporteVentasProducto(filtro);
        byte[] archivo = excelService.exportarVentasProductoExcel(datos, filtro);
        return respuesta(archivo, EXCEL_MIME, "reporte-ventas-producto-" + hoy() + ".xlsx");
    }

    @PostMapping("/ventas-producto/pdf")
    @PreAuthorize("hasAuthority('reportes:exportar')")
    public ResponseEntity<byte[]> pdfVentas(@RequestBody FiltroReporteDTO filtro) {
        List<ReporteVentasProductoItemDTO> datos = reporteService.generarReporteVentasProducto(filtro);
        byte[] archivo = pdfReporteService.exportarVentasProductoPDF(datos, filtro, nombreUsuario());
        return respuesta(archivo, MediaType.APPLICATION_PDF_VALUE, "reporte-ventas-producto-" + hoy() + ".pdf");
    }

    // ===================== MOVIMIENTOS =====================
    @PostMapping("/movimientos/preview")
    @PreAuthorize("hasAuthority('reportes:ver')")
    public ResponseEntity<List<ReporteMovimientosItemDTO>> previewMovimientos(@RequestBody FiltroReporteDTO filtro) {
        return ResponseEntity.ok(reporteService.generarReporteMovimientos(filtro));
    }

    @PostMapping("/movimientos/excel")
    @PreAuthorize("hasAuthority('reportes:exportar')")
    public ResponseEntity<byte[]> excelMovimientos(@RequestBody FiltroReporteDTO filtro) {
        List<ReporteMovimientosItemDTO> datos = reporteService.generarReporteMovimientos(filtro);
        byte[] archivo = excelService.exportarMovimientosExcel(datos, filtro);
        return respuesta(archivo, EXCEL_MIME, "reporte-movimientos-" + hoy() + ".xlsx");
    }

    @PostMapping("/movimientos/pdf")
    @PreAuthorize("hasAuthority('reportes:exportar')")
    public ResponseEntity<byte[]> pdfMovimientos(@RequestBody FiltroReporteDTO filtro) {
        List<ReporteMovimientosItemDTO> datos = reporteService.generarReporteMovimientos(filtro);
        byte[] archivo = pdfReporteService.exportarMovimientosPDF(datos, filtro, nombreUsuario());
        return respuesta(archivo, MediaType.APPLICATION_PDF_VALUE, "reporte-movimientos-" + hoy() + ".pdf");
    }

    // ===================== COSTOS DE PRODUCCIÓN (F29) =====================
    @PostMapping("/costos-produccion/preview")
    @PreAuthorize("hasAuthority('reportes:ver')")
    public ResponseEntity<List<ReporteCostosProduccionItemDTO>> previewCostosProduccion(@RequestBody FiltroReporteDTO filtro) {
        return ResponseEntity.ok(reporteService.generarReporteCostosProduccion(filtro));
    }

    @PostMapping("/costos-produccion/excel")
    @PreAuthorize("hasAuthority('reportes:exportar')")
    public ResponseEntity<byte[]> excelCostosProduccion(@RequestBody FiltroReporteDTO filtro) {
        List<ReporteCostosProduccionItemDTO> datos = reporteService.generarReporteCostosProduccion(filtro);
        byte[] archivo = excelService.exportarCostosProduccionExcel(datos, filtro);
        return respuesta(archivo, EXCEL_MIME, "reporte-costos-produccion-" + hoy() + ".xlsx");
    }

    @PostMapping("/costos-produccion/pdf")
    @PreAuthorize("hasAuthority('reportes:exportar')")
    public ResponseEntity<byte[]> pdfCostosProduccion(@RequestBody FiltroReporteDTO filtro) {
        List<ReporteCostosProduccionItemDTO> datos = reporteService.generarReporteCostosProduccion(filtro);
        byte[] archivo = pdfReporteService.exportarCostosProduccionPDF(datos, filtro, nombreUsuario());
        return respuesta(archivo, MediaType.APPLICATION_PDF_VALUE, "reporte-costos-produccion-" + hoy() + ".pdf");
    }

    // ===================== HELPERS =====================
    private ResponseEntity<byte[]> respuesta(byte[] archivo, String mime, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mime));
        headers.setContentDispositionFormData("attachment", filename);
        return ResponseEntity.ok().headers(headers).body(archivo);
    }

    private String hoy() {
        return LocalDate.now().format(FECHA_ARCHIVO);
    }

    private String nombreUsuario() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Usuario usuario) {
            return (usuario.getNombre() + " " + usuario.getApellido()).trim();
        }
        return "Sistema";
    }
}
