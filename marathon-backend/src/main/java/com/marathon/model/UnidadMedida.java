package com.marathon.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "unidad_medida")
public class UnidadMedida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_unidad_medida")
    private Integer idUnidadMedida;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "abreviatura", nullable = false)
    private String abreviatura;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public UnidadMedida() {}

    public Integer getIdUnidadMedida() { return idUnidadMedida; }
    public void setIdUnidadMedida(Integer idUnidadMedida) { this.idUnidadMedida = idUnidadMedida; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getAbreviatura() { return abreviatura; }
    public void setAbreviatura(String abreviatura) { this.abreviatura = abreviatura; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
