package com.marathon.dto.pedido;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class PedidoRequestDTO {

    @NotNull(message = "El cliente es obligatorio")
    private Integer idCliente;

    private BigDecimal descuento;

    @NotEmpty(message = "Debe incluir al menos un detalle")
    @Valid
    private List<DetallePedidoItemDTO> detalles;

    public PedidoRequestDTO() {}

    public Integer getIdCliente() { return idCliente; }
    public void setIdCliente(Integer idCliente) { this.idCliente = idCliente; }

    public BigDecimal getDescuento() { return descuento; }
    public void setDescuento(BigDecimal descuento) { this.descuento = descuento; }

    public List<DetallePedidoItemDTO> getDetalles() { return detalles; }
    public void setDetalles(List<DetallePedidoItemDTO> detalles) { this.detalles = detalles; }
}
