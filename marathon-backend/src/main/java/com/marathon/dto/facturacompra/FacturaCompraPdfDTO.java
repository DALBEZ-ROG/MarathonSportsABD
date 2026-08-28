package com.marathon.dto.facturacompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Todo lo que hay que imprimir en el documento de compra (F66).
 *
 * <p>Existe porque el PDF necesita cosas que {@link FacturaCompraResponseDTO} no
 * lleva —las lineas de la orden, quien la solicito, quien la aprobo— y no tenia
 * sentido engordar la respuesta de la API con datos que solo usa la impresion.
 */
public class FacturaCompraPdfDTO {

    private Integer idFacturaCompra;
    private String numeroFacturaProveedor;
    private LocalDate fechaFactura;
    private LocalDate fechaVencimiento;
    private BigDecimal subtotal;
    private BigDecimal impuesto;
    private BigDecimal total;
    private String estado;
    private BigDecimal ivaPorcentaje;

    private Integer idOrdenCompra;
    private LocalDateTime fechaOrden;
    private LocalDateTime fechaAprobacion;
    private String estadoOrden;
    private String observacionesOrden;

    private String proveedorNombre;
    private String solicitante;
    private String aprobador;
    private String registradoPor;

    /** Lo que se recibio de cada linea. Es lo que se factura. */
    private List<LineaPdf> lineas;

    /** Valor total recibido de la orden, para poder decir si esto es parcial. */
    private BigDecimal valorRecibidoTotal;
    /** Lo que ya se habia facturado antes de este documento. */
    private BigDecimal yaFacturadoAntes;

    public static class LineaPdf {
        private String item;
        private String tipoItem;
        private Integer cantidadPedida;
        private Integer cantidadRecibida;
        private BigDecimal precioUnitario;
        private BigDecimal importe;

        public LineaPdf() {}

        public LineaPdf(String item, String tipoItem, Integer cantidadPedida,
                        Integer cantidadRecibida, BigDecimal precioUnitario, BigDecimal importe) {
            this.item = item;
            this.tipoItem = tipoItem;
            this.cantidadPedida = cantidadPedida;
            this.cantidadRecibida = cantidadRecibida;
            this.precioUnitario = precioUnitario;
            this.importe = importe;
        }

        public String getItem() { return item; }
        public void setItem(String item) { this.item = item; }
        public String getTipoItem() { return tipoItem; }
        public void setTipoItem(String tipoItem) { this.tipoItem = tipoItem; }
        public Integer getCantidadPedida() { return cantidadPedida; }
        public void setCantidadPedida(Integer c) { this.cantidadPedida = c; }
        public Integer getCantidadRecibida() { return cantidadRecibida; }
        public void setCantidadRecibida(Integer c) { this.cantidadRecibida = c; }
        public BigDecimal getPrecioUnitario() { return precioUnitario; }
        public void setPrecioUnitario(BigDecimal p) { this.precioUnitario = p; }
        public BigDecimal getImporte() { return importe; }
        public void setImporte(BigDecimal i) { this.importe = i; }
    }

    public Integer getIdFacturaCompra() { return idFacturaCompra; }
    public void setIdFacturaCompra(Integer v) { this.idFacturaCompra = v; }
    public String getNumeroFacturaProveedor() { return numeroFacturaProveedor; }
    public void setNumeroFacturaProveedor(String v) { this.numeroFacturaProveedor = v; }
    public LocalDate getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(LocalDate v) { this.fechaFactura = v; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate v) { this.fechaVencimiento = v; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal v) { this.subtotal = v; }
    public BigDecimal getImpuesto() { return impuesto; }
    public void setImpuesto(BigDecimal v) { this.impuesto = v; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal v) { this.total = v; }
    public String getEstado() { return estado; }
    public void setEstado(String v) { this.estado = v; }
    public BigDecimal getIvaPorcentaje() { return ivaPorcentaje; }
    public void setIvaPorcentaje(BigDecimal v) { this.ivaPorcentaje = v; }
    public Integer getIdOrdenCompra() { return idOrdenCompra; }
    public void setIdOrdenCompra(Integer v) { this.idOrdenCompra = v; }
    public LocalDateTime getFechaOrden() { return fechaOrden; }
    public void setFechaOrden(LocalDateTime v) { this.fechaOrden = v; }
    public LocalDateTime getFechaAprobacion() { return fechaAprobacion; }
    public void setFechaAprobacion(LocalDateTime v) { this.fechaAprobacion = v; }
    public String getEstadoOrden() { return estadoOrden; }
    public void setEstadoOrden(String v) { this.estadoOrden = v; }
    public String getObservacionesOrden() { return observacionesOrden; }
    public void setObservacionesOrden(String v) { this.observacionesOrden = v; }
    public String getProveedorNombre() { return proveedorNombre; }
    public void setProveedorNombre(String v) { this.proveedorNombre = v; }
    public String getSolicitante() { return solicitante; }
    public void setSolicitante(String v) { this.solicitante = v; }
    public String getAprobador() { return aprobador; }
    public void setAprobador(String v) { this.aprobador = v; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String v) { this.registradoPor = v; }
    public List<LineaPdf> getLineas() { return lineas; }
    public void setLineas(List<LineaPdf> v) { this.lineas = v; }
    public BigDecimal getValorRecibidoTotal() { return valorRecibidoTotal; }
    public void setValorRecibidoTotal(BigDecimal v) { this.valorRecibidoTotal = v; }
    public BigDecimal getYaFacturadoAntes() { return yaFacturadoAntes; }
    public void setYaFacturadoAntes(BigDecimal v) { this.yaFacturadoAntes = v; }
}
