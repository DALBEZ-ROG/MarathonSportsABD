package com.marathon.dto.reporte;

import java.math.BigDecimal;
import java.util.List;

/**
 * F30 — Resumen para el dashboard de manufactura.
 */
public class ResumenManufacturaDTO {

    private Long ordenesPlanificadas;
    private Long ordenesEnProceso;
    private Long ordenesCompletadasMes;
    private Long unidadesProducidasMes;
    private BigDecimal costoProduccionMes;
    private BigDecimal mermaPromedioMes;      // porcentaje
    private Long materiaPrimaBajoMinimo;
    private List<ProductoFabricadoTopDTO> top3ProductosFabricados;

    public ResumenManufacturaDTO() {}

    public Long getOrdenesPlanificadas() { return ordenesPlanificadas; }
    public void setOrdenesPlanificadas(Long v) { this.ordenesPlanificadas = v; }

    public Long getOrdenesEnProceso() { return ordenesEnProceso; }
    public void setOrdenesEnProceso(Long v) { this.ordenesEnProceso = v; }

    public Long getOrdenesCompletadasMes() { return ordenesCompletadasMes; }
    public void setOrdenesCompletadasMes(Long v) { this.ordenesCompletadasMes = v; }

    public Long getUnidadesProducidasMes() { return unidadesProducidasMes; }
    public void setUnidadesProducidasMes(Long v) { this.unidadesProducidasMes = v; }

    public BigDecimal getCostoProduccionMes() { return costoProduccionMes; }
    public void setCostoProduccionMes(BigDecimal v) { this.costoProduccionMes = v; }

    public BigDecimal getMermaPromedioMes() { return mermaPromedioMes; }
    public void setMermaPromedioMes(BigDecimal v) { this.mermaPromedioMes = v; }

    public Long getMateriaPrimaBajoMinimo() { return materiaPrimaBajoMinimo; }
    public void setMateriaPrimaBajoMinimo(Long v) { this.materiaPrimaBajoMinimo = v; }

    public List<ProductoFabricadoTopDTO> getTop3ProductosFabricados() { return top3ProductosFabricados; }
    public void setTop3ProductosFabricados(List<ProductoFabricadoTopDTO> v) { this.top3ProductosFabricados = v; }

    /** Distribución de OP por estado (para el gráfico de dona). */
    private List<EstadoOrdenProduccionDTO> ordenesPorEstado;

    public List<EstadoOrdenProduccionDTO> getOrdenesPorEstado() { return ordenesPorEstado; }
    public void setOrdenesPorEstado(List<EstadoOrdenProduccionDTO> v) { this.ordenesPorEstado = v; }

    public static class ProductoFabricadoTopDTO {
        private Integer idProducto;
        private String producto;
        private Long unidades;

        public ProductoFabricadoTopDTO() {}

        public ProductoFabricadoTopDTO(Integer idProducto, String producto, Long unidades) {
            this.idProducto = idProducto;
            this.producto = producto;
            this.unidades = unidades;
        }

        public Integer getIdProducto() { return idProducto; }
        public void setIdProducto(Integer v) { this.idProducto = v; }

        public String getProducto() { return producto; }
        public void setProducto(String v) { this.producto = v; }

        public Long getUnidades() { return unidades; }
        public void setUnidades(Long v) { this.unidades = v; }
    }

    public static class EstadoOrdenProduccionDTO {
        private String estado;
        private Long cantidad;

        public EstadoOrdenProduccionDTO() {}

        public EstadoOrdenProduccionDTO(String estado, Long cantidad) {
            this.estado = estado;
            this.cantidad = cantidad;
        }

        public String getEstado() { return estado; }
        public void setEstado(String v) { this.estado = v; }

        public Long getCantidad() { return cantidad; }
        public void setCantidad(Long v) { this.cantidad = v; }
    }
}
