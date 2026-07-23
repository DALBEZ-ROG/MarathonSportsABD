package com.marathon.dto.ordencompra;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class OrdenCompraDetalleItemDTO {

    @NotNull(message = "El tipo de ítem es obligatorio")
    @Pattern(regexp = "producto|materia_prima",
             message = "El tipo de ítem debe ser 'producto' o 'materia_prima'")
    private String tipoItem;

    private Integer idProducto;

    private Integer idMateriaPrima;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor a 0")
    private Integer cantidad;

    @NotNull(message = "El precio unitario es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio unitario debe ser mayor a 0")
    private BigDecimal precioUnitario;

    public OrdenCompraDetalleItemDTO() {}

    public String getTipoItem() { return tipoItem; }
    public void setTipoItem(String tipoItem) { this.tipoItem = tipoItem; }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public Integer getIdMateriaPrima() { return idMateriaPrima; }
    public void setIdMateriaPrima(Integer idMateriaPrima) { this.idMateriaPrima = idMateriaPrima; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
}
