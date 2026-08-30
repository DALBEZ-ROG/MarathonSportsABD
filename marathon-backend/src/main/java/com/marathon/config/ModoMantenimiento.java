package com.marathon.config;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cierra la aplicacion al trafico normal mientras se restaura la base (F92).
 *
 * <p><b>Por que hace falta.</b> {@code pg_restore --clean} borra y vuelve a
 * crear las tablas de {@code public}. Una peticion que llegue en mitad de eso
 * no se encuentra un dato viejo: se encuentra una tabla que no existe, o peor,
 * una que existe a medias. El error que le llega al usuario seria un 500
 * incomprensible, y la operacion que estuviera haciendo podria quedar a medio
 * escribir sobre datos que van a ser reemplazados de todos modos.
 *
 * <p>Un 503 con un mensaje claro es la respuesta honesta: el sistema no esta
 * roto, esta ocupado, y se sabe hasta cuando.
 *
 * <p><b>Que sigue abierto.</b> Solo {@code /api/respaldos/**}, para que la
 * propia pantalla pueda seguir preguntando por el estado y enterarse de cuando
 * ha terminado. Si tambien se cerrara eso, quien lanzo la restauracion se
 * quedaria mirando una pantalla congelada sin saber si va bien o ha fallado.
 *
 * <h2>La pantalla se quedaba sin sesion en mitad de su propia operacion</h2>
 *
 * Dejar {@code /api/respaldos/**} fuera del 503 no bastaba, y se descubrio
 * probandolo de verdad: a los cuatro segundos de empezar la restauracion,
 * {@code GET /api/respaldos/estado} devolvia <b>401</b>.
 *
 * <p>La causa: el filtro JWT resuelve el token <i>consultando la tabla
 * {@code usuario}</i> en cada peticion —a proposito desde la F48, para que un
 * cambio de permisos surta efecto sin volver a entrar—. Durante la restauracion
 * esa tabla se esta borrando y recreando, asi que la consulta falla y la
 * peticion sale como no autenticada. Es decir: la unica pantalla que tenia que
 * sobrevivir era la primera en caerse, y encima justo cuando alguien la esta
 * mirando con inquietud.
 *
 * <p>La salida es servir el estado <b>desde memoria</b>, antes de que la cadena
 * de seguridad llegue a tocar la base. Lo que se publica asi es el progreso de
 * la tarea y una foto de los contadores tomada antes de empezar: ningun dato de
 * negocio. Y no revela nada que no revele ya el propio 503, que a cualquiera le
 * dice que hay una restauracion en marcha.
 *
 * <p>El estado es de proceso, no de base de datos: si el backend se reinicia,
 * el modo mantenimiento se levanta solo. Guardarlo en la base seria guardarlo
 * justo en el sitio que se esta reemplazando.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ModoMantenimiento extends OncePerRequestFilter {

    /** La ruta que la pantalla de respaldos sondea cada dos segundos. */
    private static final String RUTA_ESTADO = "/api/respaldos/estado";

    /** Nulo = sistema abierto. Con texto = cerrado, y el texto dice por que. */
    private final AtomicReference<String> motivo = new AtomicReference<>(null);

    /** Lo que se responde en {@link #RUTA_ESTADO} mientras dura el mantenimiento. */
    private final AtomicReference<Supplier<Object>> estadoEnMemoria = new AtomicReference<>(null);

    private final ObjectMapper json;

    public ModoMantenimiento(ObjectMapper json) {
        this.json = json;
    }

    /**
     * @param porQue frase para el 503, en lenguaje de persona
     * @param estado de donde sacar la respuesta de /api/respaldos/estado sin
     *               tocar la base; puede ser {@code null}, y entonces esa ruta
     *               tambien recibe el 503
     */
    public void activar(String porQue, Supplier<Object> estado) {
        estadoEnMemoria.set(estado);
        motivo.set(porQue);
    }

    public void desactivar() {
        motivo.set(null);
        estadoEnMemoria.set(null);
    }

    public boolean activo() {
        return motivo.get() != null;
    }

    public String motivo() {
        return motivo.get();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String razon = motivo.get();
        String ruta = request.getRequestURI();

        if (razon == null) {
            chain.doFilter(request, response);
            return;
        }

        // El sondeo de la pantalla, contestado desde memoria y sin pasar por la
        // cadena de seguridad, que necesitaria leer la tabla usuario mientras se
        // esta reemplazando. Ver la explicacion de arriba.
        Supplier<Object> foto = estadoEnMemoria.get();
        if (foto != null && "GET".equals(request.getMethod()) && RUTA_ESTADO.equals(ruta)) {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            json.writeValue(response.getWriter(), foto.get());
            return;
        }

        boolean exento = ruta.startsWith("/api/respaldos")
                      || ruta.startsWith("/api/auth/")
                      || !ruta.startsWith("/api/");

        if (exento) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Retry-After en segundos: es la cabecera estandar para esto, y evita
        // que un cliente automatico se ponga a reintentar en bucle.
        response.setHeader("Retry-After", "30");
        response.getWriter().write(
            "{\"status\":503,\"error\":\"Mantenimiento\",\"message\":\""
            + razon.replace("\"", "'") + "\"}");
    }
}
