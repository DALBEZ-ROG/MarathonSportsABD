package com.marathon.dto.pago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PagoProveedorResponseDTO {

    private Integer idPago;
    private Integer idCuentaPagar;
    private BigDecimal monto;
    private LocalDateTime fechaPago;
    private String metodoPago;
    private String referencia;
    private String observaciones;
    private String usuarioRegistroNombre;
    private BigDecimal saldoResultante;

    public PagoProveedorResponseDTO() {}

    public Integer getIdPago() { return idPago; }
    public void setIdPago(Integer idPago) { this.idPago = idPago; }

    public Integer getIdCuentaPagar() { return idCuentaPagar; }
    public void setIdCuentaPagar(Integer idCuentaPagar) { this.idCuentaPagar = idCuentaPagar; }

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

    public String getUsuarioRegistroNombre() { return usuarioRegistroNombre; }
    public void setUsuarioRegistroNombre(String usuarioRegistroNombre) { this.usuarioRegistroNombre = usuarioRegistroNombre; }

    public BigDecimal getSaldoResultante() { return saldoResultante; }
    public void setSaldoResultante(BigDecimal saldoResultante) { this.saldoResultante = saldoResultante; }
}
