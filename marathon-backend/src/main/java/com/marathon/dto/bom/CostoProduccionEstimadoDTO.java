package com.marathon.dto.bom;

import java.math.BigDecimal;
import java.util.List;

/**
 * Costo estimado de producir 1 unidad de un producto fabricado, sumando
 * cantidad_necesaria * costo_unitario_promedio de cada materia prima del BOM.
 * (F29 — ya calculable con el costo promedio ponderado de materia_prima.)
 *
 * NO incluye mano de obra ni indirectos: esos son específicos de cada orden
 * de producción real. Ver campo {@code advertencia}.
 */
public class CostoProduccionEstimadoDTO {

    private Integer idProducto;
    private String nombreProducto;
    private List<CostoMaterialEstimadoDTO> materiales;
    private BigDecimal costoMateriaPrimaUnitario;
    private BigDecimal precioVenta;
    private BigDecimal margenBruto;
    private BigDecimal margenPorcentaje;
    private String advertencia;

    public CostoProduccionEstimadoDTO() {}

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public List<CostoMaterialEstimadoDTO> getMateriales() { return materiales; }
    public void setMateriales(List<CostoMaterialEstimadoDTO> materiales) { this.materiales = materiales; }

    public BigDecimal getCostoMateriaPrimaUnitario() { return costoMateriaPrimaUnitario; }
    public void setCostoMateriaPrimaUnitario(BigDecimal v) { this.costoMateriaPrimaUnitario = v; }

    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }

    public BigDecimal getMargenBruto() { return margenBruto; }
    public void setMargenBruto(BigDecimal margenBruto) { this.margenBruto = margenBruto; }

    public BigDecimal getMargenPorcentaje() { return margenPorcentaje; }
    public void setMargenPorcentaje(BigDecimal margenPorcentaje) { this.margenPorcentaje = margenPorcentaje; }

    public String getAdvertencia() { return advertencia; }
    public void setAdvertencia(String advertencia) { this.advertencia = advertencia; }

    public static class CostoMaterialEstimadoDTO {
        private Integer idMateriaPrima;
        private String nombre;
        private BigDecimal cantidadNecesaria;
        private BigDecimal costoUnitarioPromedio;
        private BigDecimal costoLinea;

        public CostoMaterialEstimadoDTO() {}

        public CostoMaterialEstimadoDTO(Integer idMateriaPrima, String nombre,
                                        BigDecimal cantidadNecesaria, BigDecimal costoUnitarioPromedio,
                                        BigDecimal costoLinea) {
            this.idMateriaPrima = idMateriaPrima;
            this.nombre = nombre;
            this.cantidadNecesaria = cantidadNecesaria;
            this.costoUnitarioPromedio = costoUnitarioPromedio;
            this.costoLinea = costoLinea;
        }

        public Integer getIdMateriaPrima() { return idMateriaPrima; }
        public void setIdMateriaPrima(Integer v) { this.idMateriaPrima = v; }

        public String getNombre() { return nombre; }
        public void setNombre(String v) { this.nombre = v; }

        public BigDecimal getCantidadNecesaria() { return cantidadNecesaria; }
        public void setCantidadNecesaria(BigDecimal v) { this.cantidadNecesaria = v; }

        public BigDecimal getCostoUnitarioPromedio() { return costoUnitarioPromedio; }
        public void setCostoUnitarioPromedio(BigDecimal v) { this.costoUnitarioPromedio = v; }

        public BigDecimal getCostoLinea() { return costoLinea; }
        public void setCostoLinea(BigDecimal v) { this.costoLinea = v; }
    }
}
