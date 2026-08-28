package com.marathon.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una sesión cerrada a propósito (F60, D-23).
 *
 * <p>Un JWT no se puede borrar: va firmado y el servidor no guarda estado sobre
 * él. La única revocación posible es una lista de denegación que se consulte en
 * cada petición, y esta entidad es una fila de esa lista.
 *
 * <p><b>Se guarda el {@code jti}, no el token.</b> El token es la credencial; el
 * jti solo lo nombra. Si esta tabla se filtra, no sirve para entrar en ningún
 * sitio — que es justo lo contrario de lo que pasaría guardando el token entero.
 *
 * <p>La clave primaria es el propio jti y no un IDENTITY, que es la primera vez
 * que ocurre en este esquema: el identificador tiene que ser el mismo que viaja
 * dentro del JWT, así que lo genera la aplicación al firmarlo, no la base.
 */
@Entity
@Table(name = "token_revocado")
public class TokenRevocado {

    /** Identificador único del token (claim {@code jti}), un UUID. */
    @Id
    @Column(name = "jti", length = 36)
    private String jti;

    @Column(name = "correo", nullable = false, length = 150)
    private String correo;

    /** {@code acceso} o {@code refresco}. Lo restringe un CHECK en la tabla. */
    @Column(name = "tipo", nullable = false, length = 10)
    private String tipo;

    /**
     * La pone la base (DEFAULT CURRENT_TIMESTAMP), igual que {@code LogAccion.fecha}:
     * la hora de la revocación es la del servidor de base de datos, no la de la JVM.
     */
    @Column(name = "fecha_revocacion", insertable = false, updatable = false)
    private LocalDateTime fechaRevocacion;

    /**
     * Cuándo habría caducado el token por sí mismo. Marca desde cuándo esta fila
     * sobra: a partir de ahí el token se rechaza por expirado, esté aquí o no.
     */
    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    public TokenRevocado() {}

    public TokenRevocado(String jti, String correo, String tipo, LocalDateTime fechaExpiracion) {
        this.jti = jti;
        this.correo = correo;
        this.tipo = tipo;
        this.fechaExpiracion = fechaExpiracion;
    }

    public String getJti() { return jti; }
    public void setJti(String jti) { this.jti = jti; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public LocalDateTime getFechaRevocacion() { return fechaRevocacion; }

    public LocalDateTime getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(LocalDateTime fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }
}
