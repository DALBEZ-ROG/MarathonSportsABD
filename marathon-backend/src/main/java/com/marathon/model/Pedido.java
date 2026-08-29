package com.marathon.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pedido")
// F49 (D-39): @DynamicInsert es OBLIGATORIO aqui, no una optimizacion.
// Sin el, Hibernate nombra en el INSERT TODAS las columnas mapeadas, incluidas
// las que solo se rellenan en una etapa POSTERIOR del flujo:
//   numero_hu, id_transportista, fecha_empaque — las pone el empaque.
// La fase 34 concede privilegios columna por columna, asi que el rol que
// ARRANCA el flujo
// no las tiene y la base rechaza el INSERT entero con "permiso denegado".
// Con @DynamicInsert solo se nombran las columnas con valor, y el INSERT pasa.
// Ver marathon-backend/sql/fase49_privilegios_de_creacion.sql.
@DynamicInsert
@DynamicUpdate
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Integer idPedido;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_pedido", insertable = false, updatable = false)
    private LocalDateTime fechaPedido;

    @Column(name = "total", insertable = false, updatable = false)
    private BigDecimal total;

    @Column(name = "descuento", nullable = false)
    private BigDecimal descuento;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "es_pedido_especial", nullable = false)
    private Boolean esPedidoEspecial = false;

    @Column(name = "tipo_especial")
    private String tipoEspecial;

    @Column(name = "nota_especial")
    private String notaEspecial;

    @Column(name = "fecha_limite_entrega")
    private LocalDateTime fechaLimiteEntrega;

    @Column(name = "numero_hu")
    private String numeroHu;

    // F84: antes era `transportista VARCHAR(100)`, el NOMBRE escrito a mano.
    // Guardar el nombre en vez de la clave significa que renombrar un
    // transportista en el catalogo deja los pedidos viejos apuntando a algo que
    // ya no existe, y que nada impide escribir uno que no esta en la lista.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_transportista")
    private Transportista transportista;

    @Column(name = "fecha_empaque")
    private LocalDateTime fechaEmpaque;

    public Pedido() {}

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDateTime getFechaPedido() { return fechaPedido; }

    public BigDecimal getTotal() { return total; }

    public BigDecimal getDescuento() { return descuento; }
    public void setDescuento(BigDecimal descuento) { this.descuento = descuento; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public Boolean getEsPedidoEspecial() { return esPedidoEspecial; }
    public void setEsPedidoEspecial(Boolean esPedidoEspecial) { this.esPedidoEspecial = esPedidoEspecial; }

    public String getTipoEspecial() { return tipoEspecial; }
    public void setTipoEspecial(String tipoEspecial) { this.tipoEspecial = tipoEspecial; }

    public String getNotaEspecial() { return notaEspecial; }
    public void setNotaEspecial(String notaEspecial) { this.notaEspecial = notaEspecial; }

    public LocalDateTime getFechaLimiteEntrega() { return fechaLimiteEntrega; }
    public void setFechaLimiteEntrega(LocalDateTime fechaLimiteEntrega) { this.fechaLimiteEntrega = fechaLimiteEntrega; }

    public String getNumeroHu() { return numeroHu; }
    public void setNumeroHu(String numeroHu) { this.numeroHu = numeroHu; }

    public Transportista getTransportista() { return transportista; }
    public void setTransportista(Transportista transportista) { this.transportista = transportista; }

    /** El nombre, para pintarlo. Nulo mientras el pedido no se haya empacado. */
    public String getTransportistaNombre() {
        return transportista != null ? transportista.getNombre() : null;
    }

    /**
     * La region a la que va el bulto.
     *
     * <p>F84: <b>se deduce, ya no se guarda.</b> Hasta la F84 esto era una
     * columna de {@code pedido} que el empaque tecleaba, cuando el camino ya
     * estaba: pedido &rarr; cliente &rarr; ciudad &rarr; region. Un no-clave
     * determinando otro no-clave es una dependencia transitiva, y rompe la 3FN.
     *
     * <p>Se podria haber defendido como «foto del momento del envio», pero aqui
     * no cuela: el pedido <b>no guarda ninguna direccion de envio</b>, se lee
     * siempre la viva del cliente. Congelar solo la region mientras la direccion
     * es la actual da el peor resultado posible —un informe que dice «Costa» al
     * lado de una direccion de Quito—. O se congela el destino entero, o nada.
     */
    public String getRegionDestino() {
        return cliente != null && cliente.getCiudad() != null
                ? cliente.getCiudad().getRegion() : null;
    }

    public LocalDateTime getFechaEmpaque() { return fechaEmpaque; }
    public void setFechaEmpaque(LocalDateTime fechaEmpaque) { this.fechaEmpaque = fechaEmpaque; }
}
