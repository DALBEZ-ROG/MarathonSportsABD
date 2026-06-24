package com.marathon.dto.dashboard;

import java.math.BigDecimal;

public class TopProductoDTO {

    private Integer idProducto;
    private String nombreProducto;
    private String categoria;
    private Long totalVendido;
    private BigDecimal totalIngresos;

    public TopProductoDTO() {}

    public TopProductoDTO(Integer idProducto, String nombreProducto, String categoria,
                          Long totalVendido, BigDecimal totalIngresos) {
        this.idProducto = idProducto;
        this.nombreProducto = nombreProducto;
        this.categoria = categoria;
        this.totalVendido = totalVendido;
        this.totalIngresos = totalIngresos;
    }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public Long getTotalVendido() { return totalVendido; }
    public void setTotalVendido(Long totalVendido) { this.totalVendido = totalVendido; }

    public BigDecimal getTotalIngresos() { return totalIngresos; }
    public void setTotalIngresos(BigDecimal totalIngresos) { this.totalIngresos = totalIngresos; }
}
