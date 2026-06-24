package com.marathon.dto.auditoria;

import java.time.LocalDateTime;

public class AuditoriaHistorialDTO {

    private Integer idHistorial;
    private LocalDateTime fecha;
    private String producto;
    private String bodega;
    private Integer stockAnterior;
    private Integer stockNuevo;
    private Integer diferencia;
    private String motivo;
    private String usuario;

    public AuditoriaHistorialDTO() {}

    public Integer getIdHistorial() { return idHistorial; }
    public void setIdHistorial(Integer idHistorial) { this.idHistorial = idHistorial; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }

    public String getBodega() { return bodega; }
    public void setBodega(String bodega) { this.bodega = bodega; }

    public Integer getStockAnterior() { return stockAnterior; }
    public void setStockAnterior(Integer stockAnterior) { this.stockAnterior = stockAnterior; }

    public Integer getStockNuevo() { return stockNuevo; }
    public void setStockNuevo(Integer stockNuevo) { this.stockNuevo = stockNuevo; }

    public Integer getDiferencia() { return diferencia; }
    public void setDiferencia(Integer diferencia) { this.diferencia = diferencia; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
}
