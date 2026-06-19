package com.marathon.dto.pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class PedidoRequestDTO {

    @NotNull(message = "El cliente es obligatorio")
    private Integer idCliente;

    private BigDecimal descuento;

    @NotEmpty(message = "Debe incluir al menos un detalle")
    @Valid
    private List<DetallePedidoItemDTO> detalles;

    private Boolean esPedidoEspecial = false;

    @Pattern(regexp = "personalizado|regalo|corporativo|", message = "Tipo especial inválido")
    private String tipoEspecial;

    private String notaEspecial;

    private LocalDateTime fechaLimiteEntrega;

    public PedidoRequestDTO() {}

    public Integer getIdCliente() { return idCliente; }
    public void setIdCliente(Integer idCliente) { this.idCliente = idCliente; }

    public BigDecimal getDescuento() { return descuento; }
    public void setDescuento(BigDecimal descuento) { this.descuento = descuento; }

    public List<DetallePedidoItemDTO> getDetalles() { return detalles; }
    public void setDetalles(List<DetallePedidoItemDTO> detalles) { this.detalles = detalles; }

    public Boolean getEsPedidoEspecial() { return esPedidoEspecial; }
    public void setEsPedidoEspecial(Boolean esPedidoEspecial) { this.esPedidoEspecial = esPedidoEspecial; }

    public String getTipoEspecial() { return tipoEspecial; }
    public void setTipoEspecial(String tipoEspecial) { this.tipoEspecial = tipoEspecial; }

    public String getNotaEspecial() { return notaEspecial; }
    public void setNotaEspecial(String notaEspecial) { this.notaEspecial = notaEspecial; }

    public LocalDateTime getFechaLimiteEntrega() { return fechaLimiteEntrega; }
    public void setFechaLimiteEntrega(LocalDateTime fechaLimiteEntrega) { this.fechaLimiteEntrega = fechaLimiteEntrega; }
}
