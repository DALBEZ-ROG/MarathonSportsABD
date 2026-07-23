package com.marathon.dto.facturacompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class FacturaCompraResponseDTO {

    private Integer idFacturaCompra;
    private Integer idOrdenCompra;
    private String proveedorNombre;
    private String numeroFacturaProveedor;
    private LocalDate fechaFactura;
    private LocalDate fechaVencimiento;
    private BigDecimal subtotal;
    private BigDecimal impuesto;
    private BigDecimal total;
    private String estado;
    private LocalDateTime createdAt;
    private String usuarioRegistroNombre;
    private CuentaPorPagarAnidadaDTO cuentaPorPagar;

    public FacturaCompraResponseDTO() {}

    public Integer getIdFacturaCompra() { return idFacturaCompra; }
    public void setIdFacturaCompra(Integer idFacturaCompra) { this.idFacturaCompra = idFacturaCompra; }

    public Integer getIdOrdenCompra() { return idOrdenCompra; }
    public void setIdOrdenCompra(Integer idOrdenCompra) { this.idOrdenCompra = idOrdenCompra; }

    public String getProveedorNombre() { return proveedorNombre; }
    public void setProveedorNombre(String proveedorNombre) { this.proveedorNombre = proveedorNombre; }

    public String getNumeroFacturaProveedor() { return numeroFacturaProveedor; }
    public void setNumeroFacturaProveedor(String numeroFacturaProveedor) { this.numeroFacturaProveedor = numeroFacturaProveedor; }

    public LocalDate getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(LocalDate fechaFactura) { this.fechaFactura = fechaFactura; }

    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getImpuesto() { return impuesto; }
    public void setImpuesto(BigDecimal impuesto) { this.impuesto = impuesto; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getUsuarioRegistroNombre() { return usuarioRegistroNombre; }
    public void setUsuarioRegistroNombre(String usuarioRegistroNombre) { this.usuarioRegistroNombre = usuarioRegistroNombre; }

    public CuentaPorPagarAnidadaDTO getCuentaPorPagar() { return cuentaPorPagar; }
    public void setCuentaPorPagar(CuentaPorPagarAnidadaDTO cuentaPorPagar) { this.cuentaPorPagar = cuentaPorPagar; }

    public static class CuentaPorPagarAnidadaDTO {
        private Integer idCuentaPagar;
        private BigDecimal montoTotal;
        private BigDecimal montoPagado;
        private BigDecimal saldoPendiente;
        private LocalDate fechaVencimiento;
        private String estado;

        public CuentaPorPagarAnidadaDTO() {}

        public Integer getIdCuentaPagar() { return idCuentaPagar; }
        public void setIdCuentaPagar(Integer idCuentaPagar) { this.idCuentaPagar = idCuentaPagar; }

        public BigDecimal getMontoTotal() { return montoTotal; }
        public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }

        public BigDecimal getMontoPagado() { return montoPagado; }
        public void setMontoPagado(BigDecimal montoPagado) { this.montoPagado = montoPagado; }

        public BigDecimal getSaldoPendiente() { return saldoPendiente; }
        public void setSaldoPendiente(BigDecimal saldoPendiente) { this.saldoPendiente = saldoPendiente; }

        public LocalDate getFechaVencimiento() { return fechaVencimiento; }
        public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

        public String getEstado() { return estado; }
        public void setEstado(String estado) { this.estado = estado; }
    }
}
