package com.marathon.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.marathon.config.JwtUtils;
import com.marathon.model.TokenRevocado;
import com.marathon.repository.TokenRevocadoRepository;

/**
 * La lista de denegación de sesiones (F60, cierra D-23).
 *
 * <p>Antes, {@code POST /api/auth/logout} devolvía «Sesión cerrada
 * correctamente» y no hacía absolutamente nada: el token seguía valiendo hasta
 * su expiración. Cerrar sesión en un ordenador prestado no cerraba nada.
 *
 * <p><b>Ninguno de estos metodos es {@code @Transactional}, y es a proposito.</b>
 * Los tres capturan sus propios errores para no tumbar nada, y capturar dentro
 * de una transaccion no sirve de nada: PostgreSQL la deja envenenada y Spring
 * revienta igual al confirmarla, con {@code UnexpectedRollbackException}. Cada
 * llamada al repositorio abre y cierra su propia transaccion, asi que el
 * {@code catch} queda fuera de ella y si funciona.
 *
 * <p><b>El costo, dicho claro:</b> comprobar la lista es una consulta por clave
 * primaria en <b>cada</b> petición autenticada. Es lo que hizo que esta
 * revocación se descartara antes por coste. Se asume a propósito: una sesión que
 * no se puede cerrar no es una sesión, es un token con fecha. La tabla se
 * mantiene pequeña purgando lo expirado en cada cierre.
 */
@Service
public class TokenRevocadoService {

    private final TokenRevocadoRepository repositorio;
    private final JwtUtils jwtUtils;

    public TokenRevocadoService(TokenRevocadoRepository repositorio, JwtUtils jwtUtils) {
        this.repositorio = repositorio;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Revoca un token. Es idempotente: cerrar sesión dos veces con el mismo
     * token no es un error, y llamar dos veces no debe reventar por clave
     * duplicada.
     *
     * <p>Un token ilegible o ya expirado se ignora en silencio y se devuelve
     * {@code false}: no hay nada que revocar, y hacer fallar el logout por eso
     * dejaría al usuario sin poder cerrar sesión. Cerrar sesión nunca debe
     * fallar.
     */
    public boolean revocar(String token, String tipo) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            String jti = jwtUtils.extractJti(token);
            if (jti == null || jti.isBlank()) {
                // Token firmado antes de la F60: no lleva jti, no se puede
                // nombrar. Caduca solo. No es un error del usuario.
                return false;
            }
            if (repositorio.existsById(jti)) {
                return true;
            }
            repositorio.save(new TokenRevocado(
                    jti,
                    jwtUtils.extractUsername(token),
                    tipo,
                    jwtUtils.expiracionComoFecha(token)));
            return true;
        } catch (Exception e) {
            // Token invalido, expirado o con firma que no cuadra: no hay sesion
            // que cerrar. Se ignora.
            return false;
        }
    }

    /**
     * ¿Está revocado este jti? Lo pregunta el filtro en cada petición.
     *
     * <p>Ante un fallo al consultar se responde <b>false</b> —es decir, se deja
     * pasar—. Es deliberado y conviene decirlo en voz alta: la alternativa,
     * negar ante la duda, convierte cualquier problema con la tabla en una caída
     * total del sistema para todos los usuarios a la vez. Se prefiere que una
     * lista de denegación caída degrade a la seguridad que había antes de la
     * F60 (el token vale hasta expirar, ahora 2 h) antes que tumbar la
     * aplicación entera.
     */
    public boolean estaRevocado(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return repositorio.existsById(jti);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Quita de la lista lo que ya caducó por su cuenta. Ver
     * {@link TokenRevocadoRepository#purgarExpirados}: borrar una fila expirada
     * no le devuelve la vida a ningún token.
     */
    public int purgarExpirados() {
        try {
            return repositorio.purgarExpirados(LocalDateTime.now());
        } catch (Exception e) {
            // La purga es mantenimiento: que falle no puede impedir un logout.
            return 0;
        }
    }
}
