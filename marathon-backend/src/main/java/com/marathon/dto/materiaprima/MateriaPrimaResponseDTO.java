package com.marathon.dto.materiaprima;

import java.time.LocalDateTime;

public class MateriaPrimaResponseDTO {

    private Integer idMateriaPrima;
    private String nombre;
    private String descripcion;
    private Integer idUnidadMedida;
    private String unidadMedidaNombre;
    private String estado;
    private java.math.BigDecimal stockActual;
    private java.math.BigDecimal stockMinimo;
    private Boolean stockBajo;
    private LocalDateTime createdAt;

    public MateriaPrimaResponseDTO() {}

    public java.math.BigDecimal getStockActual() { return stockActual; }
    public void setStockActual(java.math.BigDecimal stockActual) { this.stockActual = stockActual; }

    public java.math.BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(java.math.BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }

    public Boolean getStockBajo() { return stockBajo; }
    public void setStockBajo(Boolean stockBajo) { this.stockBajo = stockBajo; }

    public Integer getIdMateriaPrima() { return idMateriaPrima; }
    public void setIdMateriaPrima(Integer idMateriaPrima) { this.idMateriaPrima = idMateriaPrima; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Integer getIdUnidadMedida() { return idUnidadMedida; }
    public void setIdUnidadMedida(Integer idUnidadMedida) { this.idUnidadMedida = idUnidadMedida; }

    public String getUnidadMedidaNombre() { return unidadMedidaNombre; }
    public void setUnidadMedidaNombre(String unidadMedidaNombre) { this.unidadMedidaNombre = unidadMedidaNombre; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
