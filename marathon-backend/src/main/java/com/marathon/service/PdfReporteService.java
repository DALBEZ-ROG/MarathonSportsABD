package com.marathon.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.marathon.dto.reporte.FiltroReporteDTO;
import com.marathon.dto.reporte.ReporteMovimientosItemDTO;
import com.marathon.dto.reporte.ReportePedidosItemDTO;
import com.marathon.dto.reporte.ReporteVentasProductoItemDTO;

@Service
public class PdfReporteService {

    private static final DeviceRgb MARATHON_GREEN = new DeviceRgb(45, 90, 39); // #2d5a27
    private static final DeviceRgb VERDE_CLARO = new DeviceRgb(212, 237, 218);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ===================== PEDIDOS =====================
    public byte[] exportarPedidosPDF(List<ReportePedidosItemDTO> datos, FiltroReporteDTO filtro, String nombreUsuario) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf, PageSize.A4.rotate());
        doc.setMargins(30, 30, 30, 30);

        encabezado(doc, "MARATHON SPORTS — Reporte de Pedidos", filtro);

        Table tabla = new Table(UnitValue.createPercentArray(new float[]{1.2f, 2f, 3f, 1.5f, 1.5f, 2f, 2f}))
                .useAllAvailableWidth().setMarginTop(6);
        tabla.addHeaderCell(headerCell("# Pedido"));
        tabla.addHeaderCell(headerCell("Fecha"));
        tabla.addHeaderCell(headerCell("Cliente"));
        tabla.addHeaderCell(headerCell("Estado"));
        tabla.addHeaderCell(headerCell("Total"));
        tabla.addHeaderCell(headerCell("Región"));
        tabla.addHeaderCell(headerCell("Transportista"));

        BigDecimal totalSuma = BigDecimal.ZERO;
        for (ReportePedidosItemDTO d : datos) {
            tabla.addCell(bodyCell(str(d.getIdPedido())));
            tabla.addCell(bodyCell(d.getFechaPedido() != null ? d.getFechaPedido().format(FMT) : "-"));
            tabla.addCell(bodyCell(nz(d.getCliente())));
            tabla.addCell(bodyCell(nz(d.getEstado())));
            tabla.addCell(bodyCellRight("$ " + fmt(d.getTotal())));
            tabla.addCell(bodyCell(nz(d.getRegionDestino())));
            tabla.addCell(bodyCell(nz(d.getTransportista())));
            if (d.getTotal() != null) {
                totalSuma = totalSuma.add(d.getTotal());
            }
        }
        doc.add(tabla);

        Paragraph total = new Paragraph("Total ventas: $ " + fmt(totalSuma) + "   |   Registros: " + datos.size())
                .setBold().setFontColor(MARATHON_GREEN).setFontSize(11).setMarginTop(8)
                .setTextAlignment(TextAlignment.RIGHT);
        doc.add(total);

        pie(doc, nombreUsuario);
        doc.close();
        return baos.toByteArray();
    }

    // ===================== CONSUMO DE MATERIA PRIMA (F30) =====================
    public byte[] exportarConsumoMateriaPrimaPDF(
            List<com.marathon.dto.reporte.ReporteConsumoMateriaPrimaDTO> datos,
            FiltroReporteDTO filtro, String nombreUsuario) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf, PageSize.A4.rotate());
        doc.setMargins(30, 30, 30, 30);

        encabezado(doc, "MARATHON SPORTS — Consumo de Materia Prima", filtro);

        Table tabla = new Table(UnitValue.createPercentArray(new float[]{4f, 2f, 2.5f, 2.5f, 2f}))
                .useAllAvailableWidth().setMarginTop(6);
        tabla.addHeaderCell(headerCell("Materia Prima"));
        tabla.addHeaderCell(headerCell("Unidad"));
        tabla.addHeaderCell(headerCell("Cant. Consumida"));
        tabla.addHeaderCell(headerCell("Costo Consumido"));
        tabla.addHeaderCell(headerCell("# Órdenes"));

        BigDecimal totalCosto = BigDecimal.ZERO;
        int i = 0;
        for (com.marathon.dto.reporte.ReporteConsumoMateriaPrimaDTO d : datos) {
            boolean top = (i < 3); // resalta los 3 materiales más consumidos
            tabla.addCell(bodyCellResaltado(nz(d.getNombreMateriaPrima()), top));
            tabla.addCell(bodyCellResaltado(nz(d.getUnidadMedida()), top));
            tabla.addCell(bodyCellResaltadoRight(d.getCantidadConsumidaTotal() != null
                    ? d.getCantidadConsumidaTotal().toPlainString() : "0", top));
            tabla.addCell(bodyCellResaltadoRight("$ " + fmt(d.getCostoConsumidoTotal()), top));
            tabla.addCell(bodyCellResaltadoRight(d.getNumeroOrdenes() != null
                    ? d.getNumeroOrdenes().toString() : "0", top));
            if (d.getCostoConsumidoTotal() != null) {
                totalCosto = totalCosto.add(d.getCostoConsumidoTotal());
            }
            i++;
        }
        doc.add(tabla);

        Paragraph total = new Paragraph("Costo total consumido: $ " + fmt(totalCosto)
                + "   |   Materiales: " + datos.size())
                .setBold().setFontColor(MARATHON_GREEN).setFontSize(11).setMarginTop(8)
                .setTextAlignment(TextAlignment.RIGHT);
        doc.add(total);

        pie(doc, nombreUsuario);
        doc.close();
        return baos.toByteArray();
    }

    // ===================== EFICIENCIA DE PRODUCCIÓN (F30) =====================
    public byte[] exportarEficienciaProduccionPDF(
            List<com.marathon.dto.reporte.ReporteEficienciaProduccionDTO> datos,
            FiltroReporteDTO filtro, String nombreUsuario) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf, PageSize.A4.rotate());
        doc.setMargins(30, 30, 30, 30);

        encabezado(doc, "MARATHON SPORTS — Eficiencia de Producción", filtro);

        Table tabla = new Table(UnitValue.createPercentArray(
                new float[]{1f, 3.2f, 1.6f, 1.6f, 1.8f, 1.6f, 2f, 2f, 2f}))
                .useAllAvailableWidth().setMarginTop(6);
        tabla.addHeaderCell(headerCell("OP #"));
        tabla.addHeaderCell(headerCell("Producto"));
        tabla.addHeaderCell(headerCell("Planif."));
        tabla.addHeaderCell(headerCell("Producida"));
        tabla.addHeaderCell(headerCell("Eficiencia"));
        tabla.addHeaderCell(headerCell("Merma MP"));
        tabla.addHeaderCell(headerCell("Costo Total"));
        tabla.addHeaderCell(headerCell("C. Unitario"));
        tabla.addHeaderCell(headerCell("Fecha Fin"));

        BigDecimal totalCosto = BigDecimal.ZERO;
        // Las 3 órdenes más eficientes se resaltan
        List<Integer> topIds = datos.stream()
                .sorted((a, b) -> nvl(b.getEficienciaProduccion()).compareTo(nvl(a.getEficienciaProduccion())))
                .limit(3)
                .map(com.marathon.dto.reporte.ReporteEficienciaProduccionDTO::getIdOrdenProduccion)
                .toList();

        for (com.marathon.dto.reporte.ReporteEficienciaProduccionDTO d : datos) {
            boolean top = topIds.contains(d.getIdOrdenProduccion());
            tabla.addCell(bodyCellResaltado(str(d.getIdOrdenProduccion()), top));
            tabla.addCell(bodyCellResaltado(nz(d.getProducto()), top));
            tabla.addCell(bodyCellResaltadoRight(d.getCantidadPlanificada() != null ? d.getCantidadPlanificada().toString() : "0", top));
            tabla.addCell(bodyCellResaltadoRight(d.getCantidadProducida() != null ? d.getCantidadProducida().toString() : "0", top));
            tabla.addCell(bodyCellResaltadoRight(fmt(d.getEficienciaProduccion()) + " %", top));
            tabla.addCell(bodyCellResaltadoRight(d.getMermaTotalMateriaPrima() != null ? d.getMermaTotalMateriaPrima().toPlainString() : "0", top));
            tabla.addCell(bodyCellResaltadoRight("$ " + fmt(d.getCostoTotal()), top));
            tabla.addCell(bodyCellResaltadoRight("$ " + fmt(d.getCostoUnitario()), top));
            tabla.addCell(bodyCellResaltado(d.getFechaFin() != null ? d.getFechaFin().toString() : "-", top));
            if (d.getCostoTotal() != null) {
                totalCosto = totalCosto.add(d.getCostoTotal());
            }
        }
        doc.add(tabla);

        Paragraph total = new Paragraph("Costo total: $ " + fmt(totalCosto)
                + "   |   Órdenes: " + datos.size()
                + "   |   (resaltadas: 3 más eficientes)")
                .setBold().setFontColor(MARATHON_GREEN).setFontSize(11).setMarginTop(8)
                .setTextAlignment(TextAlignment.RIGHT);
        doc.add(total);

        pie(doc, nombreUsuario);
        doc.close();
        return baos.toByteArray();
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ===================== COSTOS DE PRODUCCIÓN (F29) =====================
    public byte[] exportarCostosProduccionPDF(
            List<com.marathon.dto.reporte.ReporteCostosProduccionItemDTO> datos,
            FiltroReporteDTO filtro, String nombreUsuario) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf, PageSize.A4.rotate());
        doc.setMargins(30, 30, 30, 30);

        encabezado(doc, "MARATHON SPORTS — Reporte de Costos de Producción", filtro);

        Table tabla = new Table(UnitValue.createPercentArray(
                new float[]{1f, 3f, 1.5f, 1.8f, 1.8f, 1.8f, 1.8f, 1.8f, 2f}))
                .useAllAvailableWidth().setMarginTop(6);
        tabla.addHeaderCell(headerCell("OP #"));
        tabla.addHeaderCell(headerCell("Producto"));
        tabla.addHeaderCell(headerCell("Cant."));
        tabla.addHeaderCell(headerCell("Costo MP"));
        tabla.addHeaderCell(headerCell("Mano Obra"));
        tabla.addHeaderCell(headerCell("Indirectos"));
        tabla.addHeaderCell(headerCell("Costo Total"));
        tabla.addHeaderCell(headerCell("C. Unitario"));
        tabla.addHeaderCell(headerCell("Fecha"));

        BigDecimal totalSuma = BigDecimal.ZERO;
        for (com.marathon.dto.reporte.ReporteCostosProduccionItemDTO d : datos) {
            tabla.addCell(bodyCell(str(d.getIdOrdenProduccion())));
            tabla.addCell(bodyCell(nz(d.getProducto())));
            tabla.addCell(bodyCellRight(d.getCantidadProducida() != null ? d.getCantidadProducida().toString() : "0"));
            tabla.addCell(bodyCellRight("$ " + fmt(d.getCostoMateriaPrima())));
            tabla.addCell(bodyCellRight("$ " + fmt(d.getCostoManoObra())));
            tabla.addCell(bodyCellRight("$ " + fmt(d.getCostoIndirecto())));
            tabla.addCell(bodyCellRight("$ " + fmt(d.getCostoTotal())));
            tabla.addCell(bodyCellRight("$ " + fmt(d.getCostoUnitario())));
            tabla.addCell(bodyCell(d.getFecha() != null ? d.getFecha().format(FMT) : "-"));
            if (d.getCostoTotal() != null) {
                totalSuma = totalSuma.add(d.getCostoTotal());
            }
        }
        doc.add(tabla);

        Paragraph total = new Paragraph("Costo total de producción: $ " + fmt(totalSuma)
                + "   |   Órdenes: " + datos.size())
                .setBold().setFontColor(MARATHON_GREEN).setFontSize(11).setMarginTop(8)
                .setTextAlignment(TextAlignment.RIGHT);
        doc.add(total);

        pie(doc, nombreUsuario);
        doc.close();
        return baos.toByteArray();
    }

    // ===================== VENTAS POR PRODUCTO =====================
    public byte[] exportarVentasProductoPDF(List<ReporteVentasProductoItemDTO> datos, FiltroReporteDTO filtro,
                                            String nombreUsuario) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf, PageSize.A4.rotate());
        doc.setMargins(30, 30, 30, 30);

        encabezado(doc, "MARATHON SPORTS — Reporte de Ventas por Producto", filtro);

        Table tabla = new Table(UnitValue.createPercentArray(new float[]{4f, 2.5f, 2f, 2f, 2f}))
                .useAllAvailableWidth().setMarginTop(6);
        tabla.addHeaderCell(headerCell("Producto"));
        tabla.addHeaderCell(headerCell("Categoría"));
        tabla.addHeaderCell(headerCell("Cant. Vendida"));
        tabla.addHeaderCell(headerCell("Ingresos"));
        tabla.addHeaderCell(headerCell("Precio Promedio"));

        BigDecimal totalIngresos = BigDecimal.ZERO;
        long totalCantidad = 0L;
        int i = 0;
        for (ReporteVentasProductoItemDTO d : datos) {
            boolean top = (i < 3);
            tabla.addCell(bodyCellResaltado(nz(d.getNombreProducto()), top));
            tabla.addCell(bodyCellResaltado(nz(d.getCategoria()), top));
            tabla.addCell(bodyCellResaltadoRight(d.getCantidadVendida() != null ? d.getCantidadVendida().toString() : "0", top));
            tabla.addCell(bodyCellResaltadoRight("$ " + fmt(d.getTotalIngresos()), top));
            tabla.addCell(bodyCellResaltadoRight("$ " + fmt(d.getPrecioPromedio()), top));
            if (d.getTotalIngresos() != null) {
                totalIngresos = totalIngresos.add(d.getTotalIngresos());
            }
            if (d.getCantidadVendida() != null) {
                totalCantidad += d.getCantidadVendida();
            }
            i++;
        }
        doc.add(tabla);

        Paragraph total = new Paragraph("Total unidades: " + totalCantidad + "   |   Total ingresos: $ "
                + fmt(totalIngresos) + "   |   Registros: " + datos.size())
                .setBold().setFontColor(MARATHON_GREEN).setFontSize(11).setMarginTop(8)
                .setTextAlignment(TextAlignment.RIGHT);
        doc.add(total);

        pie(doc, nombreUsuario);
        doc.close();
        return baos.toByteArray();
    }

    // ===================== MOVIMIENTOS =====================
    public byte[] exportarMovimientosPDF(List<ReporteMovimientosItemDTO> datos, FiltroReporteDTO filtro,
                                         String nombreUsuario) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf, PageSize.A4.rotate());
        doc.setMargins(30, 30, 30, 30);

        encabezado(doc, "MARATHON SPORTS — Reporte de Movimientos", filtro);

        Table tabla = new Table(UnitValue.createPercentArray(new float[]{2f, 1.5f, 3f, 2.5f, 1.5f, 2.5f}))
                .useAllAvailableWidth().setMarginTop(6);
        tabla.addHeaderCell(headerCell("Fecha"));
        tabla.addHeaderCell(headerCell("Tipo"));
        tabla.addHeaderCell(headerCell("Producto"));
        tabla.addHeaderCell(headerCell("Bodega"));
        tabla.addHeaderCell(headerCell("Cantidad"));
        tabla.addHeaderCell(headerCell("Usuario"));

        for (ReporteMovimientosItemDTO d : datos) {
            tabla.addCell(bodyCell(d.getFecha() != null ? d.getFecha().format(FMT) : "-"));
            tabla.addCell(bodyCell(nz(d.getTipoMovimiento())));
            tabla.addCell(bodyCell(nz(d.getProducto())));
            tabla.addCell(bodyCell(nz(d.getBodega())));
            tabla.addCell(bodyCellRight(d.getCantidad() != null ? d.getCantidad().toString() : "0"));
            tabla.addCell(bodyCell(nz(d.getUsuario())));
        }
        doc.add(tabla);

        Paragraph total = new Paragraph("Registros: " + datos.size())
                .setBold().setFontColor(MARATHON_GREEN).setFontSize(11).setMarginTop(8)
                .setTextAlignment(TextAlignment.RIGHT);
        doc.add(total);

        pie(doc, nombreUsuario);
        doc.close();
        return baos.toByteArray();
    }

    // ===================== HELPERS =====================
    private void encabezado(Document doc, String titulo, FiltroReporteDTO filtro) {
        doc.add(new Paragraph(titulo)
                .setFontColor(MARATHON_GREEN).setFontSize(18).setBold()
                .setTextAlignment(TextAlignment.LEFT));

        String desde = filtro.getDesde() != null ? filtro.getDesde().format(FMT) : "Inicio";
        String hasta = filtro.getHasta() != null ? filtro.getHasta().format(FMT) : "Actualidad";
        StringBuilder filtros = new StringBuilder();
        filtros.append("Rango: ").append(desde).append(" — ").append(hasta);
        if (filtro.getEstado() != null && !filtro.getEstado().isBlank()) {
            filtros.append("  |  Estado/Tipo: ").append(filtro.getEstado());
        }
        if (filtro.getRegionDestino() != null && !filtro.getRegionDestino().isBlank()) {
            filtros.append("  |  Región: ").append(filtro.getRegionDestino());
        }
        doc.add(new Paragraph(filtros.toString())
                .setFontSize(9).setFontColor(ColorConstants.GRAY).setMarginBottom(4));
    }

    private void pie(Document doc, String nombreUsuario) {
        doc.add(new Paragraph("Generado por " + nz(nombreUsuario) + " el " + LocalDateTime.now().format(FMT))
                .setFontSize(8).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(16));
    }

    private Cell headerCell(String texto) {
        return new Cell().add(new Paragraph(texto).setFontColor(ColorConstants.WHITE).setBold().setFontSize(9))
                .setBackgroundColor(MARATHON_GREEN).setPadding(4).setTextAlignment(TextAlignment.CENTER);
    }

    private Cell bodyCell(String texto) {
        return new Cell().add(new Paragraph(texto).setFontSize(8)).setPadding(3);
    }

    private Cell bodyCellRight(String texto) {
        return new Cell().add(new Paragraph(texto).setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setPadding(3);
    }

    private Cell bodyCellResaltado(String texto, boolean top) {
        Cell c = new Cell().add(new Paragraph(texto).setFontSize(8)).setPadding(3);
        if (top) {
            c.setBackgroundColor(VERDE_CLARO).setBold();
        }
        return c;
    }

    private Cell bodyCellResaltadoRight(String texto, boolean top) {
        Cell c = new Cell().add(new Paragraph(texto).setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setPadding(3);
        if (top) {
            c.setBackgroundColor(VERDE_CLARO).setBold();
        }
        return c;
    }

    private String nz(String s) {
        return s != null ? s : "-";
    }

    private String str(Integer i) {
        return i != null ? i.toString() : "-";
    }

    private String fmt(BigDecimal b) {
        return b != null ? b.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "0.00";
    }
}
