package com.marathon.dto.reporte;

import java.math.BigDecimal;

public class ReporteVentasProductoItemDTO {

    private Integer idProducto;
    private String nombreProducto;
    private String categoria;
    private String unidadMedida;
    private Long cantidadVendida;
    private BigDecimal totalIngresos;
    private BigDecimal precioPromedio;
    private Long numeroPedidos;

    public ReporteVentasProductoItemDTO() {}

    public ReporteVentasProductoItemDTO(Integer idProducto, String nombreProducto, String categoria,
                                        String unidadMedida, Long cantidadVendida, BigDecimal totalIngresos,
                                        BigDecimal precioPromedio, Long numeroPedidos) {
        this.idProducto = idProducto;
        this.nombreProducto = nombreProducto;
        this.categoria = categoria;
        this.unidadMedida = unidadMedida;
        this.cantidadVendida = cantidadVendida;
        this.totalIngresos = totalIngresos;
        this.precioPromedio = precioPromedio;
        this.numeroPedidos = numeroPedidos;
    }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public Long getCantidadVendida() { return cantidadVendida; }
    public void setCantidadVendida(Long cantidadVendida) { this.cantidadVendida = cantidadVendida; }

    public BigDecimal getTotalIngresos() { return totalIngresos; }
    public void setTotalIngresos(BigDecimal totalIngresos) { this.totalIngresos = totalIngresos; }

    public BigDecimal getPrecioPromedio() { return precioPromedio; }
    public void setPrecioPromedio(BigDecimal precioPromedio) { this.precioPromedio = precioPromedio; }

    public Long getNumeroPedidos() { return numeroPedidos; }
    public void setNumeroPedidos(Long numeroPedidos) { this.numeroPedidos = numeroPedidos; }
}
