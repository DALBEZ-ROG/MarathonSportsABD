package com.marathon.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Comprobaciones que se hacen al arrancar y que, si fallan, impiden que la
 * aplicacion se levante (L10, defecto D-26).
 *
 * <p><b>Por que un fail-fast y no un aviso.</b> {@code application.properties}
 * esta versionado y trae
 * {@code app.jwt.secret=${JWT_SECRET:defaultDevSecretChangeInProduction}}. Si la
 * variable de entorno no esta definida y falta
 * {@code application-local.properties}, la aplicacion arrancaba tan tranquila
 * firmando tokens con un secreto publicado en el repositorio. Con ese secreto
 * cualquiera puede fabricarse un token de administrador sin credenciales: no
 * hace falta adivinar ninguna contrasena, ni siquiera existir como usuario.
 *
 * <p>Un WARN en el registro no sirve: nadie lee los avisos de arranque, y el
 * modo de fallo es silencioso e indetectable desde fuera. Es preferible que la
 * aplicacion se niegue a arrancar y diga exactamente que falta.
 */
@Configuration
public class ComprobacionesDeArranque {

    private static final Logger log = LoggerFactory.getLogger(ComprobacionesDeArranque.class);

    /** El valor que trae el fichero versionado. Nunca puede llegar a produccion. */
    private static final String SECRETO_DE_EJEMPLO = "defaultDevSecretChangeInProduction";

    /** HMAC-SHA256 pide al menos 256 bits de clave. */
    private static final int LONGITUD_MINIMA = 32;

    @Value("${app.jwt.secret:}")
    private String secretoJwt;

    @Value("${app.datos-demo.enabled:true}")
    private boolean datosDemo;

    @PostConstruct
    void comprobar() {
        if (secretoJwt == null || secretoJwt.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret no esta definido. La aplicacion no puede firmar tokens. "
                  + "Definelo en la variable de entorno JWT_SECRET o en "
                  + "application-local.properties (que esta en .gitignore).");
        }

        if (SECRETO_DE_EJEMPLO.equals(secretoJwt)) {
            throw new IllegalStateException(
                    "app.jwt.secret conserva el valor de ejemplo que viene en "
                  + "application.properties, que esta publicado en el repositorio. "
                  + "Con ese secreto cualquiera puede falsificar un token de administrador. "
                  + "Genera uno propio (por ejemplo: openssl rand -base64 48) y ponlo en la "
                  + "variable de entorno JWT_SECRET o en application-local.properties.");
        }

        if (secretoJwt.length() < LONGITUD_MINIMA) {
            throw new IllegalStateException(
                    "app.jwt.secret mide " + secretoJwt.length() + " caracteres; hacen falta al "
                  + "menos " + LONGITUD_MINIMA + " para firmar con HMAC-SHA256 sin debilitar la clave.");
        }

        if (datosDemo) {
            // No se bloquea el arranque: dejar esto en false por defecto romperia
            // el primer arranque descrito en SETUP_COMPLETO.md y todos los
            // entornos existentes. Se avisa de forma inequivoca, y la decision
            // de apagarlo en produccion queda documentada alli.
            log.warn("========================================================================");
            log.warn("app.datos-demo.enabled=true: se crearan usuarios con contrasenas FIJAS");
            log.warn("y publicadas en el codigo fuente (DataInitializer). Sirve para la demo");
            log.warn("y para las pruebas. En un despliegue real ponlo a false y crea los");
            log.warn("usuarios a mano. Ver SETUP_COMPLETO.md, seccion 'Arranque seguro'.");
            log.warn("========================================================================");
        }
    }
}
