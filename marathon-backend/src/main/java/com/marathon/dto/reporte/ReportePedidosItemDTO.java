package com.marathon.dto.reporte;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReportePedidosItemDTO {

    private Integer idPedido;
    private LocalDateTime fechaPedido;
    private String estado;
    private String cliente;
    private String ciudad;
    private String regionDestino;
    private String transportista;
    private BigDecimal total;
    private BigDecimal descuento;
    private Boolean esPedidoEspecial;
    private String tipoEspecial;

    public ReportePedidosItemDTO() {}

    public ReportePedidosItemDTO(Integer idPedido, LocalDateTime fechaPedido, String estado, String cliente,
                                 String ciudad, String regionDestino, String transportista, BigDecimal total,
                                 BigDecimal descuento, Boolean esPedidoEspecial, String tipoEspecial) {
        this.idPedido = idPedido;
        this.fechaPedido = fechaPedido;
        this.estado = estado;
        this.cliente = cliente;
        this.ciudad = ciudad;
        this.regionDestino = regionDestino;
        this.transportista = transportista;
        this.total = total;
        this.descuento = descuento;
        this.esPedidoEspecial = esPedidoEspecial;
        this.tipoEspecial = tipoEspecial;
    }

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public LocalDateTime getFechaPedido() { return fechaPedido; }
    public void setFechaPedido(LocalDateTime fechaPedido) { this.fechaPedido = fechaPedido; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getRegionDestino() { return regionDestino; }
    public void setRegionDestino(String regionDestino) { this.regionDestino = regionDestino; }

    public String getTransportista() { return transportista; }
    public void setTransportista(String transportista) { this.transportista = transportista; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public BigDecimal getDescuento() { return descuento; }
    public void setDescuento(BigDecimal descuento) { this.descuento = descuento; }

    public Boolean getEsPedidoEspecial() { return esPedidoEspecial; }
    public void setEsPedidoEspecial(Boolean esPedidoEspecial) { this.esPedidoEspecial = esPedidoEspecial; }

    public String getTipoEspecial() { return tipoEspecial; }
    public void setTipoEspecial(String tipoEspecial) { this.tipoEspecial = tipoEspecial; }
}
