package com.marathon.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.marathon.dto.reporte.FiltroReporteDTO;
import com.marathon.dto.reporte.ReporteMovimientosItemDTO;
import com.marathon.dto.reporte.ReportePedidosItemDTO;
import com.marathon.dto.reporte.ReporteVentasProductoItemDTO;

@Service
public class ExcelService {

    private static final byte[] VERDE = new byte[]{(byte) 45, (byte) 90, (byte) 39};
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ===================== PEDIDOS =====================
    public byte[] exportarPedidosExcel(List<ReportePedidosItemDTO> datos, FiltroReporteDTO filtro) {
        XSSFWorkbook wb = new XSSFWorkbook();
        try {
            Sheet sheet = wb.createSheet("Pedidos");
            CellStyle header = headerStyle(wb);
            CellStyle filaBlanca = bodyStyle(wb, false);
            CellStyle filaGris = bodyStyle(wb, true);
            CellStyle moneda = monedaStyle(wb, false);
            CellStyle monedaGris = monedaStyle(wb, true);
            CellStyle totalStyle = totalStyle(wb);
            CellStyle totalMoneda = totalMonedaStyle(wb);

            String[] cols = {"# Pedido", "Fecha", "Cliente", "Ciudad", "Estado", "Región Destino",
                    "Transportista", "Total", "Descuento", "Especial", "Tipo Especial"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(header);
            }

            BigDecimal totalSuma = BigDecimal.ZERO;
            int r = 1;
            for (ReportePedidosItemDTO d : datos) {
                boolean gris = (r % 2 == 0);
                CellStyle txt = gris ? filaGris : filaBlanca;
                CellStyle mon = gris ? monedaGris : moneda;
                Row row = sheet.createRow(r++);
                setCell(row, 0, d.getIdPedido() != null ? d.getIdPedido().toString() : "", txt);
                setCell(row, 1, d.getFechaPedido() != null ? d.getFechaPedido().format(FMT) : "", txt);
                setCell(row, 2, nz(d.getCliente()), txt);
                setCell(row, 3, nz(d.getCiudad()), txt);
                setCell(row, 4, nz(d.getEstado()), txt);
                setCell(row, 5, nz(d.getRegionDestino()), txt);
                setCell(row, 6, nz(d.getTransportista()), txt);
                setNumber(row, 7, d.getTotal(), mon);
                setNumber(row, 8, d.getDescuento(), mon);
                setCell(row, 9, Boolean.TRUE.equals(d.getEsPedidoEspecial()) ? "Sí" : "No", txt);
                setCell(row, 10, nz(d.getTipoEspecial()), txt);
                if (d.getTotal() != null) {
                    totalSuma = totalSuma.add(d.getTotal());
                }
            }

            Row totalRow = sheet.createRow(r);
            setCell(totalRow, 6, "TOTAL", totalStyle);
            setNumber(totalRow, 7, totalSuma, totalMoneda);

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }

            crearHojaResumen(wb, filtro, datos.size(), "Total ventas", totalSuma);

            return toBytes(wb);
        } finally {
            cerrar(wb);
        }
    }

    // ===================== VENTAS POR PRODUCTO =====================
    public byte[] exportarVentasProductoExcel(List<ReporteVentasProductoItemDTO> datos, FiltroReporteDTO filtro) {
        XSSFWorkbook wb = new XSSFWorkbook();
        try {
            Sheet sheet = wb.createSheet("Ventas por Producto");
            CellStyle header = headerStyle(wb);
            CellStyle filaBlanca = bodyStyle(wb, false);
            CellStyle filaGris = bodyStyle(wb, true);
            CellStyle moneda = monedaStyle(wb, false);
            CellStyle monedaGris = monedaStyle(wb, true);
            CellStyle totalStyle = totalStyle(wb);
            CellStyle totalMoneda = totalMonedaStyle(wb);

            String[] cols = {"#", "Producto", "Categoría", "Unidad", "Cant. Vendida",
                    "Total Ingresos", "Precio Promedio", "# Pedidos"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(header);
            }

            long totalCantidad = 0L;
            BigDecimal totalIngresos = BigDecimal.ZERO;
            int r = 1;
            int idx = 1;
            for (ReporteVentasProductoItemDTO d : datos) {
                boolean gris = (r % 2 == 0);
                CellStyle txt = gris ? filaGris : filaBlanca;
                CellStyle mon = gris ? monedaGris : moneda;
                Row row = sheet.createRow(r++);
                setCell(row, 0, String.valueOf(idx++), txt);
                setCell(row, 1, nz(d.getNombreProducto()), txt);
                setCell(row, 2, nz(d.getCategoria()), txt);
                setCell(row, 3, nz(d.getUnidadMedida()), txt);
                setCell(row, 4, d.getCantidadVendida() != null ? d.getCantidadVendida().toString() : "0", txt);
                setNumber(row, 5, d.getTotalIngresos(), mon);
                setNumber(row, 6, d.getPrecioPromedio(), mon);
                setCell(row, 7, d.getNumeroPedidos() != null ? d.getNumeroPedidos().toString() : "0", txt);
                if (d.getCantidadVendida() != null) {
                    totalCantidad += d.getCantidadVendida();
                }
                if (d.getTotalIngresos() != null) {
                    totalIngresos = totalIngresos.add(d.getTotalIngresos());
                }
            }

            Row totalRow = sheet.createRow(r);
            setCell(totalRow, 3, "TOTAL", totalStyle);
            setCell(totalRow, 4, String.valueOf(totalCantidad), totalStyle);
            setNumber(totalRow, 5, totalIngresos, totalMoneda);

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }

            crearHojaResumen(wb, filtro, datos.size(), "Total ingresos", totalIngresos);

            return toBytes(wb);
        } finally {
            cerrar(wb);
        }
    }

    // ===================== MOVIMIENTOS =====================
    public byte[] exportarMovimientosExcel(List<ReporteMovimientosItemDTO> datos, FiltroReporteDTO filtro) {
        XSSFWorkbook wb = new XSSFWorkbook();
        try {
            Sheet sheet = wb.createSheet("Movimientos");
            CellStyle header = headerStyle(wb);

            CellStyle entrada = tipoStyle(wb, new byte[]{(byte) 212, (byte) 237, (byte) 218}); // verde claro
            CellStyle salida = tipoStyle(wb, new byte[]{(byte) 248, (byte) 215, (byte) 218});  // rojo claro
            CellStyle traslado = tipoStyle(wb, new byte[]{(byte) 209, (byte) 236, (byte) 241}); // azul claro
            CellStyle ajuste = tipoStyle(wb, new byte[]{(byte) 255, (byte) 243, (byte) 205});   // amarillo claro
            CellStyle normal = bodyStyle(wb, false);

            String[] cols = {"#", "Fecha", "Tipo", "Producto", "Bodega", "Bodega Destino",
                    "Cantidad", "Usuario", "Observación"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(header);
            }

            int r = 1;
            int idx = 1;
            for (ReporteMovimientosItemDTO d : datos) {
                CellStyle est = estiloPorTipo(d.getTipoMovimiento(), entrada, salida, traslado, ajuste, normal);
                Row row = sheet.createRow(r++);
                setCell(row, 0, String.valueOf(idx++), est);
                setCell(row, 1, d.getFecha() != null ? d.getFecha().format(FMT) : "", est);
                setCell(row, 2, nz(d.getTipoMovimiento()), est);
                setCell(row, 3, nz(d.getProducto()), est);
                setCell(row, 4, nz(d.getBodega()), est);
                setCell(row, 5, nz(d.getBodegaDestino()), est);
                setCell(row, 6, d.getCantidad() != null ? d.getCantidad().toString() : "0", est);
                setCell(row, 7, nz(d.getUsuario()), est);
                setCell(row, 8, nz(d.getObservacion()), est);
            }

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }

            crearHojaResumen(wb, filtro, datos.size(), null, null);

            return toBytes(wb);
        } finally {
            cerrar(wb);
        }
    }

    private CellStyle estiloPorTipo(String tipo, CellStyle entrada, CellStyle salida,
                                    CellStyle traslado, CellStyle ajuste, CellStyle normal) {
        if (tipo == null) {
            return normal;
        }
        String t = tipo.toLowerCase();
        if (t.contains("entrada")) {
            return entrada;
        }
        if (t.contains("salida")) {
            return salida;
        }
        if (t.contains("traslado") || t.contains("transferencia")) {
            return traslado;
        }
        if (t.contains("ajuste")) {
            return ajuste;
        }
        return normal;
    }

    private void crearHojaResumen(XSSFWorkbook wb, FiltroReporteDTO filtro, int totalRegistros,
                                  String etiquetaTotal, BigDecimal valorTotal) {
        Sheet sheet = wb.createSheet("Resumen");
        CellStyle label = wb.createCellStyle();
        XSSFFont bold = wb.createFont();
        bold.setBold(true);
        label.setFont(bold);

        int r = 0;
        Row titulo = sheet.createRow(r++);
        Cell tc = titulo.createCell(0);
        tc.setCellValue("MARATHON SPORTS — Resumen del Reporte");
        tc.setCellStyle(label);

        fila(sheet, r++, "Total de registros", String.valueOf(totalRegistros), label);
        if (etiquetaTotal != null && valorTotal != null) {
            fila(sheet, r++, etiquetaTotal, "$ " + valorTotal.toPlainString(), label);
        }
        String desde = filtro.getDesde() != null ? filtro.getDesde().format(FMT) : "Inicio";
        String hasta = filtro.getHasta() != null ? filtro.getHasta().format(FMT) : "Actualidad";
        fila(sheet, r++, "Rango de fechas", desde + " — " + hasta, label);
        fila(sheet, r++, "Generado por", "Sistema", label);
        fila(sheet, r++, "Fecha de generación", LocalDateTime.now().format(FMT), label);

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void fila(Sheet sheet, int r, String etiqueta, String valor, CellStyle label) {
        Row row = sheet.createRow(r);
        Cell c0 = row.createCell(0);
        c0.setCellValue(etiqueta);
        c0.setCellStyle(label);
        row.createCell(1).setCellValue(valor);
    }

    // ===================== ESTILOS =====================
    private CellStyle headerStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(VERDE, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle bodyStyle(XSSFWorkbook wb, boolean gris) {
        XSSFCellStyle style = wb.createCellStyle();
        if (gris) {
            style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 245, (byte) 245, (byte) 245}, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }

    private CellStyle monedaStyle(XSSFWorkbook wb, boolean gris) {
        XSSFCellStyle style = (XSSFCellStyle) bodyStyle(wb, gris);
        style.setDataFormat(wb.createDataFormat().getFormat("\"$\"#,##0.00"));
        return style;
    }

    private CellStyle tipoStyle(XSSFWorkbook wb, byte[] rgb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(rgb, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle totalStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle totalMonedaStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = (XSSFCellStyle) totalStyle(wb);
        style.setDataFormat(wb.createDataFormat().getFormat("\"$\"#,##0.00"));
        return style;
    }

    // ===================== HELPERS =====================
    private void setCell(Row row, int col, String valor, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(valor != null ? valor : "");
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private void setNumber(Row row, int col, BigDecimal valor, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(valor != null ? valor.doubleValue() : 0d);
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private String nz(String s) {
        return s != null ? s : "";
    }

    private byte[] toBytes(XSSFWorkbook wb) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al generar el archivo Excel", e);
        }
    }

    private void cerrar(XSSFWorkbook wb) {
        try {
            wb.close();
        } catch (IOException e) {
            // ignorar al cerrar
        }
    }
}
