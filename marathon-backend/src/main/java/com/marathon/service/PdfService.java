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
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.marathon.dto.comprobante.ComprobanteResponseDTO;
import com.marathon.dto.pedido.DetallePedidoResponseDTO;

@Service
public class PdfService {

    private static final DeviceRgb MARATHON_GREEN = new DeviceRgb(45, 90, 39); // #2d5a27
    private static final DeviceRgb GRIS_CLARO = new DeviceRgb(245, 245, 245);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] generarComprobanteInternoPDF(ComprobanteResponseDTO c) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf, PageSize.A4);
        doc.setMargins(36, 36, 36, 36);

        // ENCABEZADO
        Paragraph titulo = new Paragraph("MARATHON SPORTS")
                .setFontColor(MARATHON_GREEN)
                .setFontSize(24)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        doc.add(titulo);

        Paragraph subtitulo = new Paragraph("Sistema de Gestión de Pedidos")
                .setFontSize(11)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER);
        doc.add(subtitulo);

        doc.add(new LineSeparator(new SolidLine(1f)).setMarginTop(8).setMarginBottom(12));

        // DATOS DEL COMPROBANTE
        doc.add(seccionTitulo("COMPROBANTE INTERNO"));
        Paragraph numero = new Paragraph("N° " + nz(c.getNumeroComprobante()))
                .setFontSize(14).setBold().setFontColor(MARATHON_GREEN);
        doc.add(numero);
        doc.add(linea("Fecha de emisión: ", c.getFechaEmision() != null ? c.getFechaEmision().format(FMT) : "-"));
        doc.add(linea("Estado: ", nz(c.getEstado()).toUpperCase()));

        // DATOS DEL CLIENTE
        doc.add(seccionTitulo("DATOS DEL CLIENTE"));
        doc.add(linea("Nombre: ", nz(c.getClienteNombre()) + " " + nz(c.getClienteApellido())));
        doc.add(linea("Correo: ", nz(c.getClienteCorreo())));
        doc.add(linea("Ciudad: ", nz(c.getClienteCiudad())));

        // DATOS DEL PEDIDO
        doc.add(seccionTitulo("DATOS DEL PEDIDO"));
        doc.add(linea("N° Pedido: ", c.getIdPedido() != null ? c.getIdPedido().toString() : "-"));
        doc.add(linea("Fecha del pedido: ", c.getFechaPedido() != null ? c.getFechaPedido().format(FMT) : "-"));
        doc.add(linea("Procesado por: ", nz(c.getUsuarioNombre()) + " " + nz(c.getUsuarioApellido())));

        if (Boolean.TRUE.equals(c.getEsPedidoEspecial())) {
            doc.add(linea("Tipo especial: ", nz(c.getTipoEspecial()).toUpperCase()));
            if (c.getNotaEspecial() != null && !c.getNotaEspecial().isEmpty()) {
                doc.add(linea("Nota especial: ", c.getNotaEspecial()));
            }
            if (c.getFechaLimiteEntrega() != null) {
                doc.add(linea("Fecha límite de entrega: ", c.getFechaLimiteEntrega().format(FMT)));
            }
        }

        // TABLA DE PRODUCTOS
        doc.add(seccionTitulo("DETALLE DE PRODUCTOS"));
        Table tabla = new Table(UnitValue.createPercentArray(new float[]{1, 4, 2, 2, 2}))
                .useAllAvailableWidth().setMarginTop(4);

        tabla.addHeaderCell(headerCell("#"));
        tabla.addHeaderCell(headerCell("Producto"));
        tabla.addHeaderCell(headerCell("Cantidad"));
        tabla.addHeaderCell(headerCell("Precio Unit."));
        tabla.addHeaderCell(headerCell("Subtotal"));

        List<DetallePedidoResponseDTO> detalles = c.getDetalles();
        int i = 1;
        if (detalles != null) {
            for (DetallePedidoResponseDTO d : detalles) {
                tabla.addCell(bodyCell(String.valueOf(i++)));
                tabla.addCell(bodyCell(nz(d.getProductoNombre())));
                tabla.addCell(bodyCell(d.getCantidad() != null ? d.getCantidad().toString() : "0"));
                tabla.addCell(bodyCell("$ " + fmt(d.getPrecioUnitario())));
                tabla.addCell(bodyCell("$ " + fmt(d.getSubtotal())));
            }
        }
        doc.add(tabla);

        // TOTALES
        BigDecimal subtotalSuma = BigDecimal.ZERO;
        if (detalles != null) {
            for (DetallePedidoResponseDTO d : detalles) {
                if (d.getSubtotal() != null) subtotalSuma = subtotalSuma.add(d.getSubtotal());
            }
        }

        Table totales = new Table(UnitValue.createPercentArray(new float[]{3, 2}))
                .useAllAvailableWidth().setMarginTop(8);
        totales.addCell(totalLabel("Subtotal:"));
        totales.addCell(totalValue("$ " + fmt(subtotalSuma)));
        totales.addCell(totalLabel("Descuento:"));
        totales.addCell(totalValue("$ " + fmt(c.getDescuento())));
        totales.addCell(totalLabelBold("TOTAL:"));
        totales.addCell(totalValueBold("$ " + fmt(c.getTotal())));
        doc.add(totales);

        // PIE DE PÁGINA
        doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginTop(20).setMarginBottom(6));
        Paragraph pie = new Paragraph("Documento generado el " + LocalDateTime.now().format(FMT))
                .setFontSize(8).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER);
        doc.add(pie);
        Paragraph pie2 = new Paragraph("Marathon Sports — Sistema interno")
                .setFontSize(8).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER);
        doc.add(pie2);

        doc.close();
        return baos.toByteArray();
    }

    private Paragraph seccionTitulo(String texto) {
        return new Paragraph(texto)
                .setFontSize(11).setBold().setFontColor(MARATHON_GREEN)
                .setMarginTop(12).setMarginBottom(2);
    }

    private Paragraph linea(String label, String valor) {
        Paragraph p = new Paragraph().setFontSize(10).setMarginBottom(1);
        p.add(new com.itextpdf.layout.element.Text(label).setBold());
        p.add(new com.itextpdf.layout.element.Text(valor));
        return p;
    }

    private Cell headerCell(String texto) {
        return new Cell().add(new Paragraph(texto).setFontColor(ColorConstants.WHITE).setBold().setFontSize(9))
                .setBackgroundColor(MARATHON_GREEN).setPadding(5).setTextAlignment(TextAlignment.CENTER);
    }

    private Cell bodyCell(String texto) {
        return new Cell().add(new Paragraph(texto).setFontSize(9)).setPadding(4);
    }

    private Cell totalLabel(String texto) {
        return new Cell().add(new Paragraph(texto).setFontSize(10).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER);
    }

    private Cell totalValue(String texto) {
        return new Cell().add(new Paragraph(texto).setFontSize(10).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER);
    }

    private Cell totalLabelBold(String texto) {
        return new Cell().add(new Paragraph(texto).setFontSize(12).setBold().setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setBorderTop(new SolidBorder(MARATHON_GREEN, 1));
    }

    private Cell totalValueBold(String texto) {
        return new Cell().add(new Paragraph(texto).setFontSize(12).setBold().setFontColor(MARATHON_GREEN)
                .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setBorderTop(new SolidBorder(MARATHON_GREEN, 1));
    }

    private String nz(String s) { return s != null ? s : "-"; }

    private String fmt(BigDecimal b) { return b != null ? b.setScale(2, BigDecimal.ROUND_HALF_UP).toString() : "0.00"; }
}
