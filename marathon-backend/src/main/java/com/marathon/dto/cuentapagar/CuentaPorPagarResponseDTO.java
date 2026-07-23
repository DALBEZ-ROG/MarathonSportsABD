package com.marathon.dto.cuentapagar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class CuentaPorPagarResponseDTO {

    private Integer idCuentaPagar;
    private Integer idFacturaCompra;
    private String numeroFacturaProveedor;
    private Integer idProveedor;
    private String proveedorNombre;
    private BigDecimal montoTotal;
    private BigDecimal montoPagado;
    private BigDecimal saldoPendiente;
    private LocalDate fechaVencimiento;
    private String estado;
    private LocalDateTime createdAt;
    private List<PagoDTO> pagos;

    public CuentaPorPagarResponseDTO() {}

    public Integer getIdCuentaPagar() { return idCuentaPagar; }
    public void setIdCuentaPagar(Integer idCuentaPagar) { this.idCuentaPagar = idCuentaPagar; }

    public Integer getIdFacturaCompra() { return idFacturaCompra; }
    public void setIdFacturaCompra(Integer idFacturaCompra) { this.idFacturaCompra = idFacturaCompra; }

    public String getNumeroFacturaProveedor() { return numeroFacturaProveedor; }
    public void setNumeroFacturaProveedor(String numeroFacturaProveedor) { this.numeroFacturaProveedor = numeroFacturaProveedor; }

    public Integer getIdProveedor() { return idProveedor; }
    public void setIdProveedor(Integer idProveedor) { this.idProveedor = idProveedor; }

    public String getProveedorNombre() { return proveedorNombre; }
    public void setProveedorNombre(String proveedorNombre) { this.proveedorNombre = proveedorNombre; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<PagoDTO> getPagos() { return pagos; }
    public void setPagos(List<PagoDTO> pagos) { this.pagos = pagos; }

    public static class PagoDTO {
        private Integer idPago;
        private BigDecimal monto;
        private LocalDateTime fechaPago;
        private String metodoPago;
        private String referencia;
        private String observaciones;
        private String usuarioNombre;

        public PagoDTO() {}

        public Integer getIdPago() { return idPago; }
        public void setIdPago(Integer idPago) { this.idPago = idPago; }

        public BigDecimal getMonto() { return monto; }
        public void setMonto(BigDecimal monto) { this.monto = monto; }

        public LocalDateTime getFechaPago() { return fechaPago; }
        public void setFechaPago(LocalDateTime fechaPago) { this.fechaPago = fechaPago; }

        public String getMetodoPago() { return metodoPago; }
        public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

        public String getReferencia() { return referencia; }
        public void setReferencia(String referencia) { this.referencia = referencia; }

        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

        public String getUsuarioNombre() { return usuarioNombre; }
        public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }
    }
}
