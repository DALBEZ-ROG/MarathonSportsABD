package com.marathon.dto.reporte;

import java.time.LocalDateTime;

public class FiltroReporteDTO {

    private LocalDateTime desde;
    private LocalDateTime hasta;
    private String estado;
    private Integer idCategoria;
    private String regionDestino;
    private Integer idBodega;
    private Integer idMateriaPrima;   // F30 — filtro opcional para consumo de materia prima
    private Integer idProducto;       // F30 — filtro opcional para eficiencia
    private Integer limite = 100;

    public FiltroReporteDTO() {}

    public Integer getIdMateriaPrima() { return idMateriaPrima; }
    public void setIdMateriaPrima(Integer idMateriaPrima) { this.idMateriaPrima = idMateriaPrima; }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public LocalDateTime getDesde() { return desde; }
    public void setDesde(LocalDateTime desde) { this.desde = desde; }

    public LocalDateTime getHasta() { return hasta; }
    public void setHasta(LocalDateTime hasta) { this.hasta = hasta; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Integer getIdCategoria() { return idCategoria; }
    public void setIdCategoria(Integer idCategoria) { this.idCategoria = idCategoria; }

    public String getRegionDestino() { return regionDestino; }
    public void setRegionDestino(String regionDestino) { this.regionDestino = regionDestino; }

    public Integer getIdBodega() { return idBodega; }
    public void setIdBodega(Integer idBodega) { this.idBodega = idBodega; }

    public Integer getLimite() { return limite; }
    public void setLimite(Integer limite) { this.limite = limite; }

    /** Devuelve el límite efectivo, acotado entre 1 y 1000. */
    public int getLimiteEfectivo() {
        int l = (limite != null) ? limite : 100;
        if (l < 1) {
            l = 1;
        }
        if (l > 1000) {
            l = 1000;
        }
        return l;
    }
}
