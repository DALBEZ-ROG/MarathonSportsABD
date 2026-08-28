package com.marathon.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.marathon.exception.ValidationException;

/**
 * Comprobacion de permisos desde dentro de un servicio (F48, D-13).
 *
 * <p>Casi toda la autorizacion fina se resuelve con
 * {@code @PreAuthorize("hasAuthority('modulo:accion')")} sobre el metodo del
 * controlador, que es donde se lee mejor. Esta clase existe para los dos casos
 * en los que <b>una sola llamada HTTP hace varias cosas distintas</b> y por
 * tanto no le corresponde un unico permiso:
 *
 * <ul>
 *   <li>{@code PUT /api/pedidos/{id}/estado} — cambiar de estado y anular no son
 *       lo mismo, y la matriz los distingue ({@code pedidos:editar} frente a
 *       {@code pedidos:anular}).</li>
 *   <li>{@code PUT /api/ordenes-compra/{id}/estado} — enviar a aprobacion,
 *       aprobar, rechazar y cancelar caen en el mismo endpoint y tienen
 *       repartos distintos: aprobar y rechazar son solo del Administrador.</li>
 * </ul>
 *
 * <p>Las authorities las pone {@code UsuarioDetailsService} leyendo
 * {@code rol_permiso} en CADA peticion, no del claim del token. Un cambio en la
 * pantalla de roles surte efecto en la siguiente llamada, sin volver a entrar.
 */
public final class Permisos {

    private Permisos() {}

    /** ¿El usuario de la peticion en curso tiene este permiso? */
    public static boolean tiene(String permiso) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (permiso.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * ¿Quien hace la peticion es administrador?
     *
     * <p>Se pregunta por el ROL y no por un permiso a proposito. Los permisos
     * dicen <i>que</i> puede hacer alguien y son editables desde la pantalla de
     * roles; esto pregunta <i>quien es</i>, que es otra cosa y no debe poder
     * regalarse marcando una casilla. Lo usa la excepcion de la F64 a la
     * separacion de funciones en las ordenes de compra.
     *
     * <p>Sin sesion devuelve {@code false}: el arnes de pruebas llama a los
     * servicios sin contexto de seguridad, y ahi lo prudente es NO conceder la
     * excepcion.
     */
    public static boolean esAdministrador() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_ADMINISTRADOR".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Igual, pero falla si no lo tiene.
     *
     * <p>Levanta {@code ValidationException} y no {@code AccessDeniedException}
     * porque el manejador de errores del proyecto ya traduce la primera a un 400
     * con un mensaje legible, y aqui el mensaje importa: quien lo lee necesita
     * saber <i>que</i> le falta para pedirselo a un administrador.
     *
     * @param queSeIntentaba en infinitivo, para que el mensaje se lea como una
     *                       frase ("No puedes anular pedidos.")
     */
    public static void exigir(String permiso, String queSeIntentaba) {
        if (!tiene(permiso)) {
            throw new ValidationException("No puedes " + queSeIntentaba
                    + ". Te falta el permiso '" + permiso
                    + "'; lo concede un administrador desde la pantalla de roles.");
        }
    }

    /**
     * Version que no rompe cuando no hay nadie autenticado.
     *
     * <p>Las pruebas y los arranques llaman a los servicios sin contexto de
     * seguridad. Ahi no hay a quien comprobarle nada, y fallar convertiria una
     * comprobacion de autorizacion en un obstaculo para el arnes de pruebas, que
     * no es lo que protege. El acceso ya lo filtro SecurityConfig antes de
     * llegar: esto es la capa fina, no la puerta.
     */
    public static void exigirSiHaySesion(String permiso, String queSeIntentaba) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return;
        }
        exigir(permiso, queSeIntentaba);
    }
}
