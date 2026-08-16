package com.marathon.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Formula;

/**
 * Proveedor. Sus datos de contacto estan CIFRADOS en la base desde la F41.
 *
 * <p>{@code contacto}, {@code correo}, {@code telefono} y {@code direccion} son
 * campos {@link Formula} de solo lectura sobre las columnas {@code bytea}
 * {@code contacto_enc}, {@code correo_enc}, {@code telefono_enc} y
 * {@code direccion_enc}. La explicacion completa del mecanismo esta en
 * {@link Cliente}; aqui aplica igual, con una diferencia: proveedor NO tiene
 * columna hash del correo, porque nunca tuvo {@code UNIQUE(correo)} ni hay
 * ninguna consulta que busque proveedores por correo. Anadirla habria sido
 * superficie de ataque sin funcion.
 */
@Entity
@Table(name = "proveedor")
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor")
    private Integer idProveedor;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Formula("fn_descifrar(contacto_enc)")
    private String contacto;

    @Formula("fn_descifrar(correo_enc)")
    private String correo;

    @Formula("fn_descifrar(telefono_enc)")
    private String telefono;

    @Formula("fn_descifrar(direccion_enc)")
    private String direccion;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Proveedor() {}

    public Integer getIdProveedor() { return idProveedor; }
    public void setIdProveedor(Integer idProveedor) { this.idProveedor = idProveedor; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
