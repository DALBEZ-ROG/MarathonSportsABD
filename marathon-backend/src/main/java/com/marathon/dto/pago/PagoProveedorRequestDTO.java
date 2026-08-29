package com.marathon.dto.pago;

import java.math.BigDecimal;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class PagoProveedorRequestDTO {

    @NotNull(message = "La cuenta por pagar es obligatoria")
    private Integer idCuentaPagar;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    @NotNull(message = "El método de pago es obligatorio")
    @Pattern(regexp = "transferencia|cheque|efectivo|tarjeta",
             message = "Método de pago inválido. Opciones: transferencia, cheque, efectivo, tarjeta")
    private String metodoPago;

    @Size(max = 100, message = "La referencia no puede exceder 100 caracteres")
    private String referencia;

    private String observaciones;

    public PagoProveedorRequestDTO() {}

    public Integer getIdCuentaPagar() { return idCuentaPagar; }
    public void setIdCuentaPagar(Integer idCuentaPagar) { this.idCuentaPagar = idCuentaPagar; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
