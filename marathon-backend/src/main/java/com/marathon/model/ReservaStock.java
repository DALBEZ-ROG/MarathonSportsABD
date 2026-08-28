package com.marathon.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Unidades que un pedido ya procesado tiene comprometidas (F47, D-02).
 *
 * <p>El ciclo de vida completo cabe en tres estados y lo mueven tres sitios,
 * ninguno mas:
 *
 * <ul>
 *   <li>{@code activa} — la crea {@code PedidoService.cambiarEstado} al pasar el
 *       pedido de {@code pendiente} a {@code procesado}.</li>
 *   <li>{@code consumida} — la cierra {@code EmpaqueService.confirmarEmpaque}
 *       cuando la mercancia sale de verdad del almacen.</li>
 *   <li>{@code liberada} — la cierra {@code PedidoService.cambiarEstado} al
 *       anular el pedido, o {@code ReservaStockService.liberarManualmente}
 *       cuando alguien decide soltar una reserva vencida.</li>
 * </ul>
 *
 * <p>Solo las {@code activa} descuentan del disponible.
 */
@Entity
@Table(name = "reserva_stock")
public class ReservaStock {

    public static final String ACTIVA = "activa";
    public static final String CONSUMIDA = "consumida";
    public static final String LIBERADA = "liberada";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reserva")
    private Integer idReserva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = ACTIVA;

    /**
     * Se escribe desde Java y no se deja al DEFAULT de la base a proposito: el
     * informe de vencidas se prueba retrasando esta fecha, y una columna que la
     * base rellena sola no se puede retrasar desde una prueba.
     */
    @Column(name = "fecha_reserva", nullable = false)
    private LocalDateTime fechaReserva = LocalDateTime.now();

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "motivo_cierre", length = 200)
    private String motivoCierre;

    public ReservaStock() {}

    /** Cierra la reserva. El CHECK chk_reserva_cierre exige la fecha. */
    public void cerrar(String nuevoEstado, String motivo) {
        this.estado = nuevoEstado;
        this.fechaCierre = LocalDateTime.now();
        this.motivoCierre = motivo;
    }

    public boolean estaActiva() {
        return ACTIVA.equals(estado);
    }

    public Integer getIdReserva() { return idReserva; }
    public void setIdReserva(Integer idReserva) { this.idReserva = idReserva; }

    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }

    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaReserva() { return fechaReserva; }
    public void setFechaReserva(LocalDateTime fechaReserva) { this.fechaReserva = fechaReserva; }

    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }

    public String getMotivoCierre() { return motivoCierre; }
    public void setMotivoCierre(String motivoCierre) { this.motivoCierre = motivoCierre; }
}
