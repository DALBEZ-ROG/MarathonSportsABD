package com.marathon.dto.bom;

import java.math.BigDecimal;

/**
 * Costo estimado de producir 1 unidad del producto fabricado, sumando
 * cantidad_necesaria * costo estimado de cada materia prima del BOM.
 *
 * NOTA (deuda tecnica F27): materia_prima no tiene aun un campo
 * costo_unitario_estimado, por lo que costoMateriaPrimaUnitario se
 * retorna null por ahora. Se resolvera en F29 (Costeo).
 */
public class CostoProduccionEstimadoDTO {

    private Integer idProducto;
    private String nombreProducto;
    private BigDecimal costoMateriaPrimaUnitario;

    public CostoProduccionEstimadoDTO() {}

    public CostoProduccionEstimadoDTO(Integer idProducto, String nombreProducto,
                                      BigDecimal costoMateriaPrimaUnitario) {
        this.idProducto = idProducto;
        this.nombreProducto = nombreProducto;
        this.costoMateriaPrimaUnitario = costoMateriaPrimaUnitario;
    }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public BigDecimal getCostoMateriaPrimaUnitario() { return costoMateriaPrimaUnitario; }
    public void setCostoMateriaPrimaUnitario(BigDecimal costoMateriaPrimaUnitario) { this.costoMateriaPrimaUnitario = costoMateriaPrimaUnitario; }
}
