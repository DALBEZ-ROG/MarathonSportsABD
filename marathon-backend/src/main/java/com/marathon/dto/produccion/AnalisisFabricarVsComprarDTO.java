package com.marathon.dto.produccion;

import java.math.BigDecimal;

public class AnalisisFabricarVsComprarDTO {

    private Integer idProducto;
    private String nombreProducto;
    private String categoria;
    private BigDecimal costoPromedioFabricacion;   // null si nunca se fabricó
    private Integer ordenesCompletadas;
    private BigDecimal costoPromedioCompraCategoria; // referencia de mercado
    private BigDecimal diferencia;
    private String conclusion;

    public AnalisisFabricarVsComprarDTO() {}

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer v) { this.idProducto = v; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String v) { this.nombreProducto = v; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String v) { this.categoria = v; }

    public BigDecimal getCostoPromedioFabricacion() { return costoPromedioFabricacion; }
    public void setCostoPromedioFabricacion(BigDecimal v) { this.costoPromedioFabricacion = v; }

    public Integer getOrdenesCompletadas() { return ordenesCompletadas; }
    public void setOrdenesCompletadas(Integer v) { this.ordenesCompletadas = v; }

    public BigDecimal getCostoPromedioCompraCategoria() { return costoPromedioCompraCategoria; }
    public void setCostoPromedioCompraCategoria(BigDecimal v) { this.costoPromedioCompraCategoria = v; }

    public BigDecimal getDiferencia() { return diferencia; }
    public void setDiferencia(BigDecimal v) { this.diferencia = v; }

    public String getConclusion() { return conclusion; }
    public void setConclusion(String v) { this.conclusion = v; }
}
