package com.marathon.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "movimiento_materia_prima")
public class MovimientoMateriaPrima {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento_mp")
    private Integer idMovimientoMp;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_materia_prima", nullable = false)
    private MateriaPrima materiaPrima;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "tipo_movimiento", nullable = false)
    private String tipoMovimiento;

    @Column(name = "cantidad", nullable = false)
    private BigDecimal cantidad;

    @Column(name = "stock_anterior", nullable = false)
    private BigDecimal stockAnterior;

    @Column(name = "stock_nuevo", nullable = false)
    private BigDecimal stockNuevo;

    @Column(name = "id_recepcion")
    private Integer idRecepcion;

    @Column(name = "id_orden_produccion")
    private Integer idOrdenProduccion;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    @Column(name = "fecha", insertable = false, updatable = false)
    private LocalDateTime fecha;

    public MovimientoMateriaPrima() {}

    public Integer getIdMovimientoMp() { return idMovimientoMp; }
    public void setIdMovimientoMp(Integer id) { this.idMovimientoMp = id; }
    public MateriaPrima getMateriaPrima() { return materiaPrima; }
    public void setMateriaPrima(MateriaPrima mp) { this.materiaPrima = mp; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario u) { this.usuario = u; }
    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String t) { this.tipoMovimiento = t; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal c) { this.cantidad = c; }
    public BigDecimal getStockAnterior() { return stockAnterior; }
    public void setStockAnterior(BigDecimal s) { this.stockAnterior = s; }
    public BigDecimal getStockNuevo() { return stockNuevo; }
    public void setStockNuevo(BigDecimal s) { this.stockNuevo = s; }
    public Integer getIdRecepcion() { return idRecepcion; }
    public void setIdRecepcion(Integer id) { this.idRecepcion = id; }
    public Integer getIdOrdenProduccion() { return idOrdenProduccion; }
    public void setIdOrdenProduccion(Integer id) { this.idOrdenProduccion = id; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String o) { this.observacion = o; }
    public LocalDateTime getFecha() { return fecha; }
}
