package com.marathon.dto.materiaprima;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MovimientoMateriaPrimaResponseDTO {

    private Integer idMovimientoMp;
    private Integer idMateriaPrima;
    private String materiaPrimaNombre;
    private String tipoMovimiento;
    private BigDecimal cantidad;
    private BigDecimal stockAnterior;
    private BigDecimal stockNuevo;
    private Integer idRecepcion;
    private Integer idOrdenProduccion;
    private String observacion;
    private LocalDateTime fecha;
    private String usuarioNombre;

    public MovimientoMateriaPrimaResponseDTO() {}

    public Integer getIdMovimientoMp() { return idMovimientoMp; }
    public void setIdMovimientoMp(Integer id) { this.idMovimientoMp = id; }
    public Integer getIdMateriaPrima() { return idMateriaPrima; }
    public void setIdMateriaPrima(Integer id) { this.idMateriaPrima = id; }
    public String getMateriaPrimaNombre() { return materiaPrimaNombre; }
    public void setMateriaPrimaNombre(String n) { this.materiaPrimaNombre = n; }
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
    public void setFecha(LocalDateTime f) { this.fecha = f; }
    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String n) { this.usuarioNombre = n; }
}
