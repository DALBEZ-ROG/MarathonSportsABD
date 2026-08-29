package com.marathon.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Quien lleva el bulto (F77).
 *
 * <p><b>Antes esto no existía.</b> El transportista era un {@code VARCHAR(100)}
 * libre en {@code pedido.transportista}, escrito a mano en cada empaque. En
 * 19.000 pedidos había exactamente <b>un</b> valor —«Servientrega», de una
 * prueba—, y con texto libre «Servientrega», «servientrega» y «Servi entrega»
 * son tres transportistas distintos para cualquier consulta: no se puede
 * responder «cuánto mandamos por cada uno» sin adivinar.
 *
 * <p>Es un catálogo de <b>solo lectura</b> desde la aplicación, como ciudad o
 * unidad de medida. Dar de alta un transportista es una decisión de negocio, no
 * una casilla de la pantalla de almacén; por eso la F77 no concede INSERT ni
 * UPDATE a ningún rol.
 */
@Entity
@Table(name = "transportista")
public class Transportista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_transportista")
    private Integer idTransportista;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** Dónde llega, en una línea. Se enseña al lado del nombre al elegir. */
    @Column(name = "cobertura", length = 100)
    private String cobertura;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    public Transportista() {}

    public Integer getIdTransportista() { return idTransportista; }
    public void setIdTransportista(Integer idTransportista) { this.idTransportista = idTransportista; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCobertura() { return cobertura; }
    public void setCobertura(String cobertura) { this.cobertura = cobertura; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
