package com.marathon.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Las cookies donde vive la sesión (F60, cierra D-27).
 *
 * <p><b>Qué cambia.</b> El token dejaba de estar en {@code localStorage}, donde
 * cualquier XSS podía leerlo y llevárselo, y pasa a una cookie {@code HttpOnly}
 * que el JavaScript de la página <b>no puede leer</b>.
 *
 * <p><b>Lo que esto NO arregla, dicho en voz alta:</b> un XSS sigue pudiendo
 * hacer peticiones en nombre del usuario — el navegador adjunta la cookie él
 * solo—. Lo que ya no puede es <b>robarse la credencial</b> y usarla desde otro
 * sitio, más tarde. Esa es exactamente la diferencia que compra {@code HttpOnly},
 * ni más ni menos.
 *
 * <p><b>Por qué CSRF sigue desactivado.</b> Al pasar la sesión a una cookie
 * aparece el riesgo de que otro sitio dispare peticiones autenticadas. Lo cierra
 * {@code SameSite=Strict}: el navegador no adjunta estas cookies a ninguna
 * petición que no venga del propio sitio. Un token CSRF de doble envío no era
 * viable aquí — el front está en {@code localhost:4300} y la API en
 * {@code localhost:8080}, así que el JavaScript del front no puede leer una
 * cookie puesta por la API—. Nótese que para {@code SameSite} el puerto no
 * cuenta: 4300 y 8080 son el <b>mismo sitio</b>, así que la cookie sí viaja en
 * las llamadas legítimas del front. Si algún día el front se sirve desde otro
 * dominio, esto hay que rehacerlo: {@code SameSite} dejaría de protegerlo y
 * haría falta CSRF de verdad.
 */
@Component
public class CookieSesion {

    public static final String COOKIE_ACCESO = "marathon_token";
    public static final String COOKIE_REFRESCO = "marathon_refresh";

    @Value("${app.jwt.expiration}")
    private long expiracionAcceso;

    /** Los 7 días del refresco, en segundos. */
    private static final long EXPIRACION_REFRESCO = 604800;

    /**
     * En producción (HTTPS) tiene que ser {@code true} o la cookie viaja en
     * claro. Se deja en {@code false} por defecto porque en desarrollo la API es
     * {@code http://localhost:8080} y una cookie {@code Secure} no se enviaría
     * nunca — la sesión no funcionaría y el motivo no se vería por ningún lado.
     */
    @Value("${app.sesion.cookie-segura:false}")
    private boolean cookieSegura;

    public ResponseCookie deAcceso(String token) {
        return construir(COOKIE_ACCESO, token, expiracionAcceso / 1000);
    }

    public ResponseCookie deRefresco(String token) {
        return construir(COOKIE_REFRESCO, token, EXPIRACION_REFRESCO);
    }

    /** Cookies vacías y caducadas: así se borra una cookie del navegador. */
    public ResponseCookie borrarAcceso() {
        return construir(COOKIE_ACCESO, "", 0);
    }

    public ResponseCookie borrarRefresco() {
        return construir(COOKIE_REFRESCO, "", 0);
    }

    private ResponseCookie construir(String nombre, String valor, long segundos) {
        return ResponseCookie.from(nombre, valor)
                .httpOnly(true)
                .secure(cookieSegura)
                .sameSite("Strict")
                .path("/")
                .maxAge(segundos)
                .build();
    }

    /** Lee una cookie de la petición, o {@code null} si no viene. */
    public static String leer(HttpServletRequest request, String nombre) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie c : request.getCookies()) {
            if (nombre.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }
}
