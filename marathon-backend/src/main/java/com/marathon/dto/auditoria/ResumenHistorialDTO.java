package com.marathon.dto.auditoria;

import java.time.LocalDateTime;

public class ResumenHistorialDTO {

    private Long totalMovimientos;
    private Integer stockMinimoRegistrado;
    private Integer stockMaximoRegistrado;
    private LocalDateTime ultimaActualizacion;

    public ResumenHistorialDTO() {}

    public ResumenHistorialDTO(Long totalMovimientos, Integer stockMinimoRegistrado,
                               Integer stockMaximoRegistrado, LocalDateTime ultimaActualizacion) {
        this.totalMovimientos = totalMovimientos;
        this.stockMinimoRegistrado = stockMinimoRegistrado;
        this.stockMaximoRegistrado = stockMaximoRegistrado;
        this.ultimaActualizacion = ultimaActualizacion;
    }

    public Long getTotalMovimientos() { return totalMovimientos; }
    public void setTotalMovimientos(Long totalMovimientos) { this.totalMovimientos = totalMovimientos; }

    public Integer getStockMinimoRegistrado() { return stockMinimoRegistrado; }
    public void setStockMinimoRegistrado(Integer stockMinimoRegistrado) { this.stockMinimoRegistrado = stockMinimoRegistrado; }

    public Integer getStockMaximoRegistrado() { return stockMaximoRegistrado; }
    public void setStockMaximoRegistrado(Integer stockMaximoRegistrado) { this.stockMaximoRegistrado = stockMaximoRegistrado; }

    public LocalDateTime getUltimaActualizacion() { return ultimaActualizacion; }
    public void setUltimaActualizacion(LocalDateTime ultimaActualizacion) { this.ultimaActualizacion = ultimaActualizacion; }
}
