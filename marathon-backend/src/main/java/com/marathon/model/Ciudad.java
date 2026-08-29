package com.marathon.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ciudad")
public class Ciudad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ciudad")
    private Integer idCiudad;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "estado", nullable = false)
    private String estado;

    /**
     * Region natural a la que pertenece la ciudad (F77): Costa, Sierra, Oriente
     * o Insular.
     *
     * <p><b>Vive aqui y no en el pedido</b>, que es donde estaba antes. La
     * region de destino se tecleaba a mano en cada empaque cuando ya se sabia:
     * el pedido tiene cliente, el cliente tiene ciudad, y la ciudad esta en una
     * region. Pedir a mano un dato deducible es la forma segura de que acabe mal
     * escrito.
     */
    @Column(name = "region", length = 20)
    private String region;

    public Ciudad() {}

    public Integer getIdCiudad() { return idCiudad; }
    public void setIdCiudad(Integer idCiudad) { this.idCiudad = idCiudad; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
