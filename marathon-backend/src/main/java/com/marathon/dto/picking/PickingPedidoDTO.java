package com.marathon.dto.picking;

import java.time.LocalDateTime;
import java.util.List;

public class PickingPedidoDTO {

    private Integer idPedido;
    private String numeroPedido;
    private String clienteNombre;
    private String clienteApellido;
    private LocalDateTime fechaPedido;
    private String estado;
    private Boolean esPedidoEspecial;
    private String tipoEspecial;
    private String notaEspecial;
    private LocalDateTime fechaLimiteEntrega;
    private List<PickingLineaDTO> lineas;
    private Integer totalLineas;
    private Integer lineasCompletadas;
    private String estadoPicking;

    public PickingPedidoDTO() {}

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public String getNumeroPedido() { return numeroPedido; }
    public void setNumeroPedido(String numeroPedido) { this.numeroPedido = numeroPedido; }

    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }

    public String getClienteApellido() { return clienteApellido; }
    public void setClienteApellido(String clienteApellido) { this.clienteApellido = clienteApellido; }

    public LocalDateTime getFechaPedido() { return fechaPedido; }
    public void setFechaPedido(LocalDateTime fechaPedido) { this.fechaPedido = fechaPedido; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Boolean getEsPedidoEspecial() { return esPedidoEspecial; }
    public void setEsPedidoEspecial(Boolean esPedidoEspecial) { this.esPedidoEspecial = esPedidoEspecial; }

    public String getTipoEspecial() { return tipoEspecial; }
    public void setTipoEspecial(String tipoEspecial) { this.tipoEspecial = tipoEspecial; }

    public String getNotaEspecial() { return notaEspecial; }
    public void setNotaEspecial(String notaEspecial) { this.notaEspecial = notaEspecial; }

    public LocalDateTime getFechaLimiteEntrega() { return fechaLimiteEntrega; }
    public void setFechaLimiteEntrega(LocalDateTime fechaLimiteEntrega) { this.fechaLimiteEntrega = fechaLimiteEntrega; }

    public List<PickingLineaDTO> getLineas() { return lineas; }
    public void setLineas(List<PickingLineaDTO> lineas) { this.lineas = lineas; }

    public Integer getTotalLineas() { return totalLineas; }
    public void setTotalLineas(Integer totalLineas) { this.totalLineas = totalLineas; }

    public Integer getLineasCompletadas() { return lineasCompletadas; }
    public void setLineasCompletadas(Integer lineasCompletadas) { this.lineasCompletadas = lineasCompletadas; }

    public String getEstadoPicking() { return estadoPicking; }
    public void setEstadoPicking(String estadoPicking) { this.estadoPicking = estadoPicking; }
}
