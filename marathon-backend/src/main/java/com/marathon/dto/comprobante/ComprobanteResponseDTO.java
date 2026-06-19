package com.marathon.dto.comprobante;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.marathon.dto.pedido.DetallePedidoResponseDTO;

public class ComprobanteResponseDTO {

    private Integer idComprobante;
    private String numeroComprobante;
    private LocalDateTime fechaEmision;
    private BigDecimal total;
    private String estado;
    private LocalDateTime createdAt;

    // Datos del pedido
    private Integer idPedido;
    private LocalDateTime fechaPedido;
    private BigDecimal descuento;
    private String estadoPedido;
    private Boolean esPedidoEspecial;
    private String tipoEspecial;
    private String notaEspecial;
    private LocalDateTime fechaLimiteEntrega;

    // Datos del cliente
    private String clienteNombre;
    private String clienteApellido;
    private String clienteCorreo;
    private String clienteCiudad;

    // Datos del usuario
    private String usuarioNombre;
    private String usuarioApellido;

    private List<DetallePedidoResponseDTO> detalles;

    public ComprobanteResponseDTO() {}

    public Integer getIdComprobante() { return idComprobante; }
    public void setIdComprobante(Integer idComprobante) { this.idComprobante = idComprobante; }

    public String getNumeroComprobante() { return numeroComprobante; }
    public void setNumeroComprobante(String numeroComprobante) { this.numeroComprobante = numeroComprobante; }

    public LocalDateTime getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDateTime fechaEmision) { this.fechaEmision = fechaEmision; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public LocalDateTime getFechaPedido() { return fechaPedido; }
    public void setFechaPedido(LocalDateTime fechaPedido) { this.fechaPedido = fechaPedido; }

    public BigDecimal getDescuento() { return descuento; }
    public void setDescuento(BigDecimal descuento) { this.descuento = descuento; }

    public String getEstadoPedido() { return estadoPedido; }
    public void setEstadoPedido(String estadoPedido) { this.estadoPedido = estadoPedido; }

    public Boolean getEsPedidoEspecial() { return esPedidoEspecial; }
    public void setEsPedidoEspecial(Boolean esPedidoEspecial) { this.esPedidoEspecial = esPedidoEspecial; }

    public String getTipoEspecial() { return tipoEspecial; }
    public void setTipoEspecial(String tipoEspecial) { this.tipoEspecial = tipoEspecial; }

    public String getNotaEspecial() { return notaEspecial; }
    public void setNotaEspecial(String notaEspecial) { this.notaEspecial = notaEspecial; }

    public LocalDateTime getFechaLimiteEntrega() { return fechaLimiteEntrega; }
    public void setFechaLimiteEntrega(LocalDateTime fechaLimiteEntrega) { this.fechaLimiteEntrega = fechaLimiteEntrega; }

    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }

    public String getClienteApellido() { return clienteApellido; }
    public void setClienteApellido(String clienteApellido) { this.clienteApellido = clienteApellido; }

    public String getClienteCorreo() { return clienteCorreo; }
    public void setClienteCorreo(String clienteCorreo) { this.clienteCorreo = clienteCorreo; }

    public String getClienteCiudad() { return clienteCiudad; }
    public void setClienteCiudad(String clienteCiudad) { this.clienteCiudad = clienteCiudad; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public String getUsuarioApellido() { return usuarioApellido; }
    public void setUsuarioApellido(String usuarioApellido) { this.usuarioApellido = usuarioApellido; }

    public List<DetallePedidoResponseDTO> getDetalles() { return detalles; }
    public void setDetalles(List<DetallePedidoResponseDTO> detalles) { this.detalles = detalles; }
}
