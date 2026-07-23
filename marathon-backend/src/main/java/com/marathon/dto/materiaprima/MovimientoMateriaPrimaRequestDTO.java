package com.marathon.dto.materiaprima;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class MovimientoMateriaPrimaRequestDTO {

    @NotNull(message = "La materia prima es obligatoria")
    private Integer idMateriaPrima;

    @NotNull(message = "El tipo de movimiento es obligatorio")
    @Pattern(regexp = "ajuste|merma", message = "Tipo invalido. Solo se permite: ajuste, merma")
    private String tipoMovimiento;

    @NotNull(message = "La cantidad es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0")
    private BigDecimal cantidad;

    private Boolean esIncremento = false;

    private String observacion;

    public MovimientoMateriaPrimaRequestDTO() {}

    public Integer getIdMateriaPrima() { return idMateriaPrima; }
    public void setIdMateriaPrima(Integer id) { this.idMateriaPrima = id; }
    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String t) { this.tipoMovimiento = t; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal c) { this.cantidad = c; }
    public Boolean getEsIncremento() { return esIncremento; }
    public void setEsIncremento(Boolean e) { this.esIncremento = e; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String o) { this.observacion = o; }
}
