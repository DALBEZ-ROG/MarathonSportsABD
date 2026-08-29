package com.marathon.dto.produccion;

import java.math.BigDecimal;

/**
 * Lo que costaría producir esta orden, propuesto por el sistema (F71).
 *
 * <p><b>Por qué existe.</b> Al completar una orden había que teclear a mano el
 * coste de mano de obra y el indirecto. Nadie los sabe: no son datos que estén
 * en ningún sitio del sistema, así que o se inventaban o se dejaban en cero — y
 * en cero el análisis de costes decía que fabricar sale exactamente lo que vale
 * la materia prima, que es falso.
 *
 * <p><b>Cómo se calculan, dicho sin adornos.</b> No salen de ningún fichaje ni
 * de ninguna factura de luz: salen de <b>dos tarifas configurables</b>, que es
 * lo que hace un sistema de costeo estándar cuando no tiene datos reales:
 *
 * <pre>
 *   mano de obra = tarifa por unidad × unidades producidas
 *   indirecto    = porcentaje × (materia prima + mano de obra)
 * </pre>
 *
 * <p>La mano de obra escala con las <b>unidades</b> porque fabricar cada una
 * cuesta un rato de trabajo. El indirecto se aplica como <b>porcentaje del coste
 * directo</b>, que es como se reparten de verdad la luz, el alquiler y la
 * maquinaria: no se pueden atribuir a una prenda concreta, así que se prorratean.
 *
 * <p><b>Son una propuesta, no una imposición.</b> Quien completa la orden puede
 * cambiarlas: si en esa tanda hubo horas extra, se escribe lo que costó. Lo que
 * se evita es el cero por omisión.
 */
public class CostosSugeridosDTO {

    /** Materia prima ya consumida, según el coste fotografiado al iniciar. */
    private BigDecimal costoMateriaPrima;

    /** La tarifa que se ha aplicado, para poder enseñarla y que no sea magia. */
    private BigDecimal manoObraPorUnidad;
    private BigDecimal costoManoObra;

    /** El porcentaje aplicado sobre el coste directo. */
    private BigDecimal indirectoPorcentaje;
    private BigDecimal costoIndirecto;

    private BigDecimal costoTotal;
    private BigDecimal costoUnitario;

    /** Las unidades sobre las que se calculó, para que cuadre a la vista. */
    private Integer cantidad;

    public BigDecimal getCostoMateriaPrima() { return costoMateriaPrima; }
    public void setCostoMateriaPrima(BigDecimal v) { this.costoMateriaPrima = v; }
    public BigDecimal getManoObraPorUnidad() { return manoObraPorUnidad; }
    public void setManoObraPorUnidad(BigDecimal v) { this.manoObraPorUnidad = v; }
    public BigDecimal getCostoManoObra() { return costoManoObra; }
    public void setCostoManoObra(BigDecimal v) { this.costoManoObra = v; }
    public BigDecimal getIndirectoPorcentaje() { return indirectoPorcentaje; }
    public void setIndirectoPorcentaje(BigDecimal v) { this.indirectoPorcentaje = v; }
    public BigDecimal getCostoIndirecto() { return costoIndirecto; }
    public void setCostoIndirecto(BigDecimal v) { this.costoIndirecto = v; }
    public BigDecimal getCostoTotal() { return costoTotal; }
    public void setCostoTotal(BigDecimal v) { this.costoTotal = v; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public void setCostoUnitario(BigDecimal v) { this.costoUnitario = v; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer v) { this.cantidad = v; }
}
