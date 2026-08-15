package com.marathon.config;

import java.text.Normalizer;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Elige el pool de conexiones segun el rol del usuario autenticado (fase 37).
 *
 * <p>Hasta la F36 toda la aplicacion se conectaba como {@code usr_admin_marathon}.
 * El modelo de privilegios de la F34 estaba construido y verificado, pero solo
 * distinguia a la aplicacion del acceso directo por psql: un operador de bodega
 * llegaba a la base con privilegios de administrador y lo unico que lo frenaba
 * era {@code SecurityConfig}. Con este enrutador cada rol funcional se conecta
 * con su propio usuario de PostgreSQL y queda sujeto a sus GRANT.
 *
 * <p><b>Cuando NO se enruta.</b> La clave se resuelve a partir del
 * {@code SecurityContext}, que en tres momentos esta vacio a proposito:
 * <ul>
 *   <li>el arranque de la aplicacion (Hibernate pide metadatos, DataInitializer
 *       siembra roles y permisos),</li>
 *   <li>el login, que tiene que leer la tabla {@code usuario} antes de saber
 *       quien es quien,</li>
 *   <li>el filtro JWT, que resuelve el token consultando la base <i>antes</i> de
 *       poblar el contexto.</li>
 * </ul>
 * En los tres casos se usa el pool por defecto, el del administrador. Es
 * deliberado: son operaciones de la propia infraestructura de autenticacion, no
 * trabajo hecho en nombre de un rol.
 *
 * <p><b>Falla cerrado.</b> Si hay un usuario autenticado cuyo rol no tiene pool
 * configurado, se lanza una excepcion en lugar de caer al pool del
 * administrador. Lo contrario convertiria un rol mal configurado en una
 * escalada de privilegios silenciosa, que es justamente lo que esta fase viene
 * a cerrar.
 */
public class RoleRoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(RoleRoutingDataSource.class);

    /** Clave del pool por defecto: el del administrador. */
    public static final String CLAVE_ADMINISTRADOR = "administrador";

    /**
     * Rol funcional (tal como esta en la tabla {@code rol}, normalizado) a clave
     * de pool. Se normaliza sin acentos para que la correspondencia no dependa
     * de como se haya codificado este archivo ni la fila de la base.
     */
    private static final Map<String, String> ROL_A_POOL = Map.of(
        "ADMINISTRADOR",           CLAVE_ADMINISTRADOR,
        "SUPERVISOR E-COMMERCE",   "supervisor",
        "OPERADOR DE BODEGA",      "operador-bodega",
        "OPERADOR DE PEDIDOS",     "operador-pedidos",
        "ENCARGADO DE COMPRAS",    "encargado-compras",
        "ENCARGADO DE PRODUCCION", "encargado-produccion"
    );

    /**
     * Marca, para el hilo en curso, que la operacion pertenece a la
     * infraestructura de autenticacion y debe usar el pool por defecto.
     */
    private static final ThreadLocal<Boolean> USAR_POOL_AUTENTICACION = new ThreadLocal<>();

    private final Set<String> poolesDisponibles;

    public RoleRoutingDataSource(Set<String> poolesDisponibles) {
        this.poolesDisponibles = poolesDisponibles;
    }

    /**
     * Ejecuta una operacion sobre las credenciales de un usuario usando el pool
     * por defecto en lugar del pool de su rol.
     *
     * <p>Existe por un caso concreto: cambiar la propia contrasena. Es un
     * {@code UPDATE} sobre la tabla {@code usuario}, y ningun rol operativo lo
     * tiene ni debe tenerlo — concederlo permitiria a cualquiera de esos roles
     * cambiar la contrasena de <i>cualquier</i> cuenta, incluida la del
     * administrador, porque un privilegio de base de datos no distingue "mi
     * fila" de "la fila de otro". Quien impone esa distincion es la
     * aplicacion, que ya comprueba la contrasena actual antes de tocar nada.
     *
     * <p>Es la misma excepcion que ya se hace con el login y el filtro JWT:
     * gestionar credenciales es trabajo de la infraestructura de
     * autenticacion, no trabajo hecho en nombre de un rol.
     *
     * <p><b>Debe envolver a la llamada transaccional desde fuera</b> (el
     * controlador), no dentro del servicio: la conexion se elige al abrir la
     * transaccion, asi que marcarlo despues no tendria efecto.
     */
    public static void conPoolDeAutenticacion(Runnable operacionSobreCredenciales) {
        USAR_POOL_AUTENTICACION.set(Boolean.TRUE);
        try {
            operacionSobreCredenciales.run();
        } finally {
            USAR_POOL_AUTENTICACION.remove();
        }
    }

    @Override
    protected Object determineCurrentLookupKey() {
        if (Boolean.TRUE.equals(USAR_POOL_AUTENTICACION.get())) {
            return null;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;   // null -> defaultTargetDataSource (administrador)
        }

        String poolNoDisponible = null;

        for (GrantedAuthority authority : auth.getAuthorities()) {
            String rol = normalizarAuthority(authority.getAuthority());
            if (rol == null) {
                continue;   // no es un ROLE_*, es un permiso "modulo:accion"
            }

            String pool = ROL_A_POOL.get(rol);
            if (pool == null) {
                continue;   // rol de aplicacion sin equivalente en la base
            }

            // El administrador gana sobre cualquier otro rol que tenga el mismo
            // usuario: es el unico pool que puede con todos los flujos.
            if (CLAVE_ADMINISTRADOR.equals(pool)) {
                return null;
            }
            if (poolesDisponibles.contains(pool)) {
                return pool;
            }
            poolNoDisponible = pool;
        }

        if (poolNoDisponible != null) {
            throw new IllegalStateException(
                "El rol '" + poolNoDisponible + "' no tiene pool de conexiones configurado. "
                + "Definir app.datasource.roles.credenciales." + poolNoDisponible
                + ".username/password, o desactivar el enrutado con "
                + "app.datasource.roles.enabled=false.");
        }

        // Autenticado pero sin ningun rol reconocible. No se cae al pool del
        // administrador: eso seria conceder privilegios por omision.
        throw new IllegalStateException(
            "El usuario '" + auth.getName() + "' no tiene ningun rol con pool de conexiones. "
            + "Roles conocidos: " + ROL_A_POOL.keySet());
    }

    /**
     * {@code "ROLE_ENCARGADO DE PRODUCCIÓN"} -> {@code "ENCARGADO DE PRODUCCION"}.
     * Devuelve {@code null} si la authority no es un rol.
     */
    private static String normalizarAuthority(String authority) {
        if (authority == null || !authority.startsWith("ROLE_")) {
            return null;
        }
        String rol = authority.substring("ROLE_".length());
        return Normalizer.normalize(rol, Normalizer.Form.NFD)
                         .replaceAll("\\p{M}", "")
                         .toUpperCase();
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        log.info("Enrutado de conexiones por rol activo. Pools disponibles: {} (+ '{}' por defecto)",
                 poolesDisponibles, CLAVE_ADMINISTRADOR);
    }
}
