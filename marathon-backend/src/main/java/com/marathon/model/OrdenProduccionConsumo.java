package com.marathon.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "orden_produccion_consumo")
public class OrdenProduccionConsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consumo")
    private Integer idConsumo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_orden_produccion", nullable = false)
    private OrdenProduccion ordenProduccion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_materia_prima", nullable = false)
    private MateriaPrima materiaPrima;

    @Column(name = "cantidad_teorica", nullable = false)
    private BigDecimal cantidadTeorica;

    @Column(name = "cantidad_real")
    private BigDecimal cantidadReal;

    // Columna GENERATED: real - teorica. Hibernate la re-lee tras INSERT/UPDATE
    // (@Generated) para que el DTO refleje siempre el valor calculado por la BD.
    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "merma", insertable = false, updatable = false)
    private BigDecimal merma;

    // F29 — Snapshot inmutable del costo promedio al momento de consumir.
    @Column(name = "costo_unitario_snapshot", nullable = false)
    private BigDecimal costoUnitarioSnapshot = BigDecimal.ZERO;

    // F29 — Columna GENERATED: COALESCE(real, teorica) * snapshot.
    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "costo_linea", insertable = false, updatable = false)
    private BigDecimal costoLinea;

    public OrdenProduccionConsumo() {}

    public Integer getIdConsumo() { return idConsumo; }
    public void setIdConsumo(Integer idConsumo) { this.idConsumo = idConsumo; }

    public OrdenProduccion getOrdenProduccion() { return ordenProduccion; }
    public void setOrdenProduccion(OrdenProduccion ordenProduccion) { this.ordenProduccion = ordenProduccion; }

    public MateriaPrima getMateriaPrima() { return materiaPrima; }
    public void setMateriaPrima(MateriaPrima materiaPrima) { this.materiaPrima = materiaPrima; }

    public BigDecimal getCantidadTeorica() { return cantidadTeorica; }
    public void setCantidadTeorica(BigDecimal cantidadTeorica) { this.cantidadTeorica = cantidadTeorica; }

    public BigDecimal getCantidadReal() { return cantidadReal; }
    public void setCantidadReal(BigDecimal cantidadReal) { this.cantidadReal = cantidadReal; }

    public BigDecimal getMerma() { return merma; }

    public BigDecimal getCostoUnitarioSnapshot() { return costoUnitarioSnapshot; }
    public void setCostoUnitarioSnapshot(BigDecimal costoUnitarioSnapshot) { this.costoUnitarioSnapshot = costoUnitarioSnapshot; }

    public BigDecimal getCostoLinea() { return costoLinea; }
}
