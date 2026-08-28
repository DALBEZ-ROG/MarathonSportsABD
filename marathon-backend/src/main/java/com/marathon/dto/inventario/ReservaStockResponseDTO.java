package com.marathon.dto.inventario;

import java.time.LocalDateTime;

/**
 * Una reserva de stock vista desde el informe (F47, D-02).
 *
 * <p>Lleva {@code diasRetenida} ya calculado porque la pregunta que contesta el
 * informe no es "¿cuando se reservo?" sino "¿cuanto lleva esto parado?", y hacer
 * esa resta en cada pantalla es como se acaba teniendo dos respuestas distintas
 * a la misma pregunta.
 */
public class ReservaStockResponseDTO {

    private Integer idReserva;
    private Integer idPedido;
    private String numeroPedido;
    private String estadoPedido;
    private Integer idProducto;
    private String productoNombre;
    private Integer cantidad;
    private String estado;
    private LocalDateTime fechaReserva;
    private long diasRetenida;
    private LocalDateTime fechaCierre;
    private String motivoCierre;

    public Integer getIdReserva() { return idReserva; }
    public void setIdReserva(Integer idReserva) { this.idReserva = idReserva; }

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public String getNumeroPedido() { return numeroPedido; }
    public void setNumeroPedido(String numeroPedido) { this.numeroPedido = numeroPedido; }

    public String getEstadoPedido() { return estadoPedido; }
    public void setEstadoPedido(String estadoPedido) { this.estadoPedido = estadoPedido; }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public String getProductoNombre() { return productoNombre; }
    public void setProductoNombre(String productoNombre) { this.productoNombre = productoNombre; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaReserva() { return fechaReserva; }
    public void setFechaReserva(LocalDateTime fechaReserva) { this.fechaReserva = fechaReserva; }

    public long getDiasRetenida() { return diasRetenida; }
    public void setDiasRetenida(long diasRetenida) { this.diasRetenida = diasRetenida; }

    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }

    public String getMotivoCierre() { return motivoCierre; }
    public void setMotivoCierre(String motivoCierre) { this.motivoCierre = motivoCierre; }
}
