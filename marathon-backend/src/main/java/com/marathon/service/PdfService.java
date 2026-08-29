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
import com.marathon.dto.facturacompra.FacturaCompraPdfDTO;
import com.marathon.dto.pedido.DetallePedidoResponseDTO;

@Service
public class PdfService {

    private static final DeviceRgb MARATHON_GREEN = new DeviceRgb(45, 90, 39); // #2d5a27
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

    /**
     * El documento de compra en PDF (F66).
     *
     * <p><b>Se llama «documento interno de compra» y no «factura», y es a
     * proposito.</b> La factura la emite el PROVEEDOR: lleva su numeracion, su
     * membrete y su firma. Esto lo emite Marathon a partir de lo que de verdad
     * entro en la bodega, asi que llamarlo factura seria decir que es algo que
     * no es. Sirve para archivar, cotejar contra el papel del proveedor y pasar
     * a contabilidad.
     *
     * <p>Las lineas muestran <b>lo pedido y lo recibido</b>, no solo lo
     * recibido. Cuando la recepcion es parcial, esa diferencia es justo lo que
     * hay que ver de un vistazo, y esconderla dejaria un documento que cuadra
     * pero no explica nada.
     */
    public byte[] generarFacturaCompraPDF(FacturaCompraPdfDTO f) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf, PageSize.A4);
        doc.setMargins(36, 36, 36, 36);

        doc.add(new Paragraph("MARATHON SPORTS")
                .setFontColor(MARATHON_GREEN).setFontSize(24).setBold()
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph("Documento interno de compra")
                .setFontSize(11).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new LineSeparator(new SolidLine(1f)).setMarginTop(8).setMarginBottom(12));

        // --- Cabecera: el documento y la orden de la que sale ---------------
        Table cab = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth().setMarginBottom(6);
        Cell izq = new Cell().setBorder(Border.NO_BORDER);
        izq.add(linea("Documento N.º: ", nz(f.getNumeroFacturaProveedor())));
        izq.add(linea("Fecha: ", f.getFechaFactura() != null ? f.getFechaFactura().toString() : "-"));
        izq.add(linea("Vencimiento: ", f.getFechaVencimiento() != null ? f.getFechaVencimiento().toString() : "-"));
        izq.add(linea("Estado: ", nz(f.getEstado())));
        Cell der = new Cell().setBorder(Border.NO_BORDER);
        der.add(linea("Proveedor: ", nz(f.getProveedorNombre())));
        der.add(linea("Orden de compra: ", "#" + f.getIdOrdenCompra()));
        der.add(linea("Fecha de la orden: ", f.getFechaOrden() != null ? f.getFechaOrden().format(FMT) : "-"));
        der.add(linea("Estado de la orden: ", nz(f.getEstadoOrden())));
        cab.addCell(izq);
        cab.addCell(der);
        doc.add(cab);

        // --- Quien hizo que. Es la separacion de funciones, impresa ---------
        doc.add(seccionTitulo("RESPONSABLES"));
        doc.add(linea("Solicitó la orden: ", nz(f.getSolicitante())));
        doc.add(linea("Aprobó la orden: ", nz(f.getAprobador())));
        doc.add(linea("Registró el documento: ", nz(f.getRegistradoPor())));

        // --- El detalle -----------------------------------------------------
        doc.add(seccionTitulo("DETALLE DE LO RECIBIDO"));
        Table t = new Table(UnitValue.createPercentArray(new float[]{40, 12, 12, 16, 20}))
                .useAllAvailableWidth().setMarginTop(4);
        t.addHeaderCell(headerCell("Item"));
        t.addHeaderCell(headerCell("Pedido"));
        t.addHeaderCell(headerCell("Recibido"));
        t.addHeaderCell(headerCell("P. unitario"));
        t.addHeaderCell(headerCell("Importe"));

        boolean hayParcial = false;
        if (f.getLineas() != null) {
            for (FacturaCompraPdfDTO.LineaPdf l : f.getLineas()) {
                int pedido = l.getCantidadPedida() != null ? l.getCantidadPedida() : 0;
                int recibido = l.getCantidadRecibida() != null ? l.getCantidadRecibida() : 0;
                if (recibido < pedido) {
                    hayParcial = true;
                }
                t.addCell(bodyCell(nz(l.getItem())));
                t.addCell(bodyCell(String.valueOf(pedido)).setTextAlignment(TextAlignment.CENTER));
                t.addCell(bodyCell(String.valueOf(recibido)).setTextAlignment(TextAlignment.CENTER));
                t.addCell(bodyCell("$ " + fmt(l.getPrecioUnitario())).setTextAlignment(TextAlignment.RIGHT));
                t.addCell(bodyCell("$ " + fmt(l.getImporte())).setTextAlignment(TextAlignment.RIGHT));
            }
        }
        doc.add(t);

        // --- Totales --------------------------------------------------------
        Table tot = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
                .useAllAvailableWidth().setMarginTop(10);
        tot.addCell(totalLabel("Subtotal"));
        tot.addCell(totalValue("$ " + fmt(f.getSubtotal())));
        String etiquetaIva = f.getIvaPorcentaje() != null
                ? "IVA (" + f.getIvaPorcentaje().stripTrailingZeros().toPlainString() + "%)"
                : "IVA";
        tot.addCell(totalLabel(etiquetaIva));
        tot.addCell(totalValue("$ " + fmt(f.getImpuesto())));
        tot.addCell(totalLabelBold("TOTAL"));
        tot.addCell(totalValueBold("$ " + fmt(f.getTotal())));
        doc.add(tot);

        // --- Lo que hay que decir cuando la recepcion no esta completa ------
        if (hayParcial) {
            doc.add(new Paragraph("Recepción parcial: este documento cubre únicamente "
                    + "las cantidades recibidas. Lo que falte por llegar se documenta "
                    + "aparte, cuando se reciba.")
                    .setFontSize(9).setItalic().setFontColor(ColorConstants.DARK_GRAY)
                    .setMarginTop(10));
        }
        if (f.getYaFacturadoAntes() != null
                && f.getYaFacturadoAntes().compareTo(BigDecimal.ZERO) > 0) {
            doc.add(new Paragraph("De esta orden ya se había documentado $ "
                    + fmt(f.getYaFacturadoAntes()) + " con anterioridad. Este documento "
                    + "cubre solo la diferencia, para no contar dos veces lo mismo.")
                    .setFontSize(9).setItalic().setFontColor(ColorConstants.DARK_GRAY)
                    .setMarginTop(4));
        }
        if (f.getObservacionesOrden() != null && !f.getObservacionesOrden().isBlank()) {
            doc.add(seccionTitulo("OBSERVACIONES DE LA ORDEN"));
            doc.add(new Paragraph(f.getObservacionesOrden()).setFontSize(9));
        }

        doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginTop(18).setMarginBottom(6));
        doc.add(new Paragraph("Documento generado por el sistema el "
                + LocalDateTime.now().format(FMT) + ". No sustituye a la factura del "
                + "proveedor: sirve para archivarlo y cotejarlo contra ella.")
                .setFontSize(8).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));

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

    private String fmt(BigDecimal b) { return b != null ? b.setScale(2, java.math.RoundingMode.HALF_UP).toString() : "0.00"; }
}
