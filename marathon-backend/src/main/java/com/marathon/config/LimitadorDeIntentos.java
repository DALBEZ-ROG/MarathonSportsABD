package com.marathon.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Freno a los intentos de login fallidos (L10, defecto D-25).
 *
 * <p>No habia ninguno: se podian probar contrasenas sin limite, y el mensaje
 * distinguia "Usuario inactivo" de "Credenciales incorrectas", con lo que ademas
 * se podia averiguar que correos corresponden a cuentas reales.
 *
 * <p>Un contador en memoria basta para este proyecto: una sola instancia, sin
 * balanceador. Si algun dia hubiera varias, esto habria que llevarlo a Redis o a
 * una tabla — y estaria mal fiarse de este contador mientras tanto.
 *
 * <p>El contador se lleva por <b>correo + IP</b>. Solo por IP dejaria fuera a
 * toda una oficina que comparte salida; solo por correo permitiria bloquear a
 * cualquiera a proposito con intentos fallidos.
 */
@Component
public class LimitadorDeIntentos {

    @Value("${app.login.max-intentos:10}")
    private int maxIntentos;

    @Value("${app.login.ventana-minutos:15}")
    private long ventanaMinutos;

    private record Contador(AtomicInteger fallos, Instant desde) {}

    private final Map<String, Contador> contadores = new ConcurrentHashMap<>();

    /** ¿Se le permite intentarlo? */
    public boolean permitido(String correo, String ip) {
        Contador c = contadores.get(clave(correo, ip));
        if (c == null) {
            return true;
        }
        if (expirado(c)) {
            contadores.remove(clave(correo, ip));
            return true;
        }
        return c.fallos().get() < maxIntentos;
    }

    public void registrarFallo(String correo, String ip) {
        contadores.compute(clave(correo, ip), (k, actual) -> {
            if (actual == null || expirado(actual)) {
                return new Contador(new AtomicInteger(1), Instant.now());
            }
            actual.fallos().incrementAndGet();
            return actual;
        });
    }

    /** Un login correcto borra el historial de fallos. */
    public void registrarExito(String correo, String ip) {
        contadores.remove(clave(correo, ip));
    }

    public int minutosDeBloqueo() {
        return (int) ventanaMinutos;
    }

    private boolean expirado(Contador c) {
        return Duration.between(c.desde(), Instant.now()).toMinutes() >= ventanaMinutos;
    }

    private static String clave(String correo, String ip) {
        return (correo == null ? "" : correo.toLowerCase()) + "|" + (ip == null ? "" : ip);
    }
}
