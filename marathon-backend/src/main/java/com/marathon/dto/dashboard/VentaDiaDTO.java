package com.marathon.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public class VentaDiaDTO {

    private LocalDate fecha;
    private BigDecimal totalVentas;
    private Long cantidadPedidos;

    public VentaDiaDTO() {}

    public VentaDiaDTO(LocalDate fecha, BigDecimal totalVentas, Long cantidadPedidos) {
        this.fecha = fecha;
        this.totalVentas = totalVentas;
        this.cantidadPedidos = cantidadPedidos;
    }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public BigDecimal getTotalVentas() { return totalVentas; }
    public void setTotalVentas(BigDecimal totalVentas) { this.totalVentas = totalVentas; }

    public Long getCantidadPedidos() { return cantidadPedidos; }
    public void setCantidadPedidos(Long cantidadPedidos) { this.cantidadPedidos = cantidadPedidos; }
}
