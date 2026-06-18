package com.marathon.dto.inventario;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class MovimientoRequestDTO {

    @NotNull(message = "El producto es obligatorio")
    private Integer idProducto;

    @NotNull(message = "La bodega es obligatoria")
    private Integer idBodega;

    private Integer idBodegaDestino;

    @NotBlank(message = "El tipo de movimiento es obligatorio")
    @Pattern(regexp = "entrada|salida|ajuste|traslado", message = "Tipo de movimiento inválido")
    private String tipoMovimiento;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidad;

    private String observacion;

    public MovimientoRequestDTO() {}

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public Integer getIdBodega() { return idBodega; }
    public void setIdBodega(Integer idBodega) { this.idBodega = idBodega; }

    public Integer getIdBodegaDestino() { return idBodegaDestino; }
    public void setIdBodegaDestino(Integer idBodegaDestino) { this.idBodegaDestino = idBodegaDestino; }

    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
