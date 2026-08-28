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
import com.marathon.dto.reporte.ReporteConsumoMateriaPrimaDTO;
import com.marathon.dto.reporte.ReporteEficienciaProduccionDTO;
import com.marathon.model.Usuario;
import com.marathon.service.ExcelService;
import com.marathon.service.PdfReporteService;
import com.marathon.service.ReporteManufacturaService;

/**
 * F30 — Reportes de manufactura. Reutiliza ExcelService y PdfReporteService (F17).
 */
@RestController
@RequestMapping("/api/reportes/manufactura")
public class ReporteManufacturaController {

    private static final String EXCEL_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FECHA_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReporteManufacturaService reporteManufacturaService;
    private final ExcelService excelService;
    private final PdfReporteService pdfReporteService;

    public ReporteManufacturaController(ReporteManufacturaService reporteManufacturaService,
                                        ExcelService excelService,
                                        PdfReporteService pdfReporteService) {
        this.reporteManufacturaService = reporteManufacturaService;
        this.excelService = excelService;
        this.pdfReporteService = pdfReporteService;
    }

    // ===================== CONSUMO DE MATERIA PRIMA =====================
    @PostMapping("/consumo-materia-prima/preview")
    @PreAuthorize("hasAuthority('reportes:manufactura')")
    public ResponseEntity<List<ReporteConsumoMateriaPrimaDTO>> previewConsumo(@RequestBody FiltroReporteDTO filtro) {
        return ResponseEntity.ok(reporteManufacturaService.consumoMateriaPrima(filtro));
    }

    @PostMapping("/consumo-materia-prima/excel")
    @PreAuthorize("hasAuthority('reportes:manufactura')")
    public ResponseEntity<byte[]> excelConsumo(@RequestBody FiltroReporteDTO filtro) {
        List<ReporteConsumoMateriaPrimaDTO> datos = reporteManufacturaService.consumoMateriaPrima(filtro);
        byte[] archivo = excelService.exportarConsumoMateriaPrimaExcel(datos, filtro);
        return respuesta(archivo, EXCEL_MIME, "reporte-consumo-materia-prima-" + hoy() + ".xlsx");
    }

    @PostMapping("/consumo-materia-prima/pdf")
    @PreAuthorize("hasAuthority('reportes:manufactura')")
    public ResponseEntity<byte[]> pdfConsumo(@RequestBody FiltroReporteDTO filtro) {
        List<ReporteConsumoMateriaPrimaDTO> datos = reporteManufacturaService.consumoMateriaPrima(filtro);
        byte[] archivo = pdfReporteService.exportarConsumoMateriaPrimaPDF(datos, filtro, nombreUsuario());
        return respuesta(archivo, MediaType.APPLICATION_PDF_VALUE, "reporte-consumo-materia-prima-" + hoy() + ".pdf");
    }

    // ===================== EFICIENCIA DE PRODUCCIÓN =====================
    @PostMapping("/eficiencia-produccion/preview")
    @PreAuthorize("hasAuthority('reportes:manufactura')")
    public ResponseEntity<List<ReporteEficienciaProduccionDTO>> previewEficiencia(@RequestBody FiltroReporteDTO filtro) {
        return ResponseEntity.ok(reporteManufacturaService.eficienciaProduccion(filtro));
    }

    @PostMapping("/eficiencia-produccion/excel")
    @PreAuthorize("hasAuthority('reportes:manufactura')")
    public ResponseEntity<byte[]> excelEficiencia(@RequestBody FiltroReporteDTO filtro) {
        List<ReporteEficienciaProduccionDTO> datos = reporteManufacturaService.eficienciaProduccion(filtro);
        byte[] archivo = excelService.exportarEficienciaProduccionExcel(datos, filtro);
        return respuesta(archivo, EXCEL_MIME, "reporte-eficiencia-produccion-" + hoy() + ".xlsx");
    }

    @PostMapping("/eficiencia-produccion/pdf")
    @PreAuthorize("hasAuthority('reportes:manufactura')")
    public ResponseEntity<byte[]> pdfEficiencia(@RequestBody FiltroReporteDTO filtro) {
        List<ReporteEficienciaProduccionDTO> datos = reporteManufacturaService.eficienciaProduccion(filtro);
        byte[] archivo = pdfReporteService.exportarEficienciaProduccionPDF(datos, filtro, nombreUsuario());
        return respuesta(archivo, MediaType.APPLICATION_PDF_VALUE, "reporte-eficiencia-produccion-" + hoy() + ".pdf");
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
