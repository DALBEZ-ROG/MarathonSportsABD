package com.marathon.dto.produccion;

import java.math.BigDecimal;

public class CostoProductoFabricadoDTO {

    private Integer idProducto;
    private String nombreProducto;
    private String categoria;
    private BigDecimal costoPromedioFabricacion; // null si nunca se fabricó
    private BigDecimal precioVenta;
    private BigDecimal margen;                    // precioVenta - costoPromedioFabricacion
    private Integer ordenesCompletadas;

    public CostoProductoFabricadoDTO() {}

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer v) { this.idProducto = v; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String v) { this.nombreProducto = v; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String v) { this.categoria = v; }

    public BigDecimal getCostoPromedioFabricacion() { return costoPromedioFabricacion; }
    public void setCostoPromedioFabricacion(BigDecimal v) { this.costoPromedioFabricacion = v; }

    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal v) { this.precioVenta = v; }

    public BigDecimal getMargen() { return margen; }
    public void setMargen(BigDecimal v) { this.margen = v; }

    public Integer getOrdenesCompletadas() { return ordenesCompletadas; }
    public void setOrdenesCompletadas(Integer v) { this.ordenesCompletadas = v; }
}
