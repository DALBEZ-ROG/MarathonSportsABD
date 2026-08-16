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

import org.hibernate.annotations.Formula;

/**
 * Cliente. Sus datos de contacto estan CIFRADOS en la base desde la F41.
 *
 * <p><b>Como funcionan {@code correo}, {@code telefono} y {@code direccion}.</b>
 * Las columnas en claro ya no existen: en su lugar hay {@code correo_enc},
 * {@code telefono_enc} y {@code direccion_enc} de tipo {@code bytea}, escritas
 * con {@code pgp_sym_encrypt}. Estos tres campos son {@link Formula}, es decir
 * <b>solo lectura</b>: Hibernate inyecta {@code fn_descifrar(columna)} en el
 * SELECT y devuelve el texto claro, siempre que la sesion tenga publicada
 * {@code app.crypto_key}. Si no la tiene, llegan a {@code null}.
 *
 * <p><b>Por que solo lectura.</b> La clave nunca entra en el JVM para cifrar:
 * el cifrado ocurre dentro de PostgreSQL. Un setter que pareciera persistir
 * seria una trampa. Las escrituras pasan por
 * {@code CifradoService.guardarDatosCliente(...)}, que ejecuta el
 * {@code UPDATE ... fn_cifrar(?)} y refresca la entidad. Los setters siguen
 * existiendo porque los DTO los usan, pero no persisten nada por si solos.
 *
 * <p>Las columnas {@code *_enc} NO se mapean como atributos. {@code ddl-auto}
 * sigue en {@code validate} y valida las entidades contra el esquema, no al
 * reves: una columna sin atributo no rompe el arranque, y mapearla invitaria a
 * escribir texto cifrado desde Java.
 */
@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer idCliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_ciudad", nullable = false)
    private Ciudad ciudad;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "apellido", nullable = false)
    private String apellido;

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

    public Cliente() {}

    public Integer getIdCliente() { return idCliente; }
    public void setIdCliente(Integer idCliente) { this.idCliente = idCliente; }

    public Ciudad getCiudad() { return ciudad; }
    public void setCiudad(Ciudad ciudad) { this.ciudad = ciudad; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

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
