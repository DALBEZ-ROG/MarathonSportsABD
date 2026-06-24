package com.marathon.dto.dashboard;

public class MovimientoResumenDTO {

    private String tipoMovimiento;
    private Long cantidad;
    private Long totalUnidades;

    public MovimientoResumenDTO() {}

    public MovimientoResumenDTO(String tipoMovimiento, Long cantidad, Long totalUnidades) {
        this.tipoMovimiento = tipoMovimiento;
        this.cantidad = cantidad;
        this.totalUnidades = totalUnidades;
    }

    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public Long getCantidad() { return cantidad; }
    public void setCantidad(Long cantidad) { this.cantidad = cantidad; }

    public Long getTotalUnidades() { return totalUnidades; }
    public void setTotalUnidades(Long totalUnidades) { this.totalUnidades = totalUnidades; }
}
