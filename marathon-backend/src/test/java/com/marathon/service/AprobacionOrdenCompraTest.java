package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.config.Permisos;

/**
 * F64 — la separación de funciones al aprobar una orden de compra, y su única
 * excepción.
 *
 * <p><b>La regla</b>: quien solicita una orden no puede aprobarla.
 *
 * <p><b>La excepción, decidida el 2026-08-28 por el dueño del proyecto</b>: el
 * Administrador sí puede aprobar la suya. El motivo fue operativo y concreto —
 * {@code compras:aprobar} lo tiene solo el Administrador y solo existe un
 * usuario con ese rol, así que una orden creada por él <b>no la podía aprobar
 * nadie</b> y el flujo se quedaba muerto sin salida.
 *
 * <p>Estas pruebas existen para que las dos mitades queden fijadas. La segunda
 * importa tanto como la primera: si algún día se concede
 * {@code compras:aprobar} a otro rol desde la pantalla de roles, ese rol
 * <b>sigue</b> sin poder aprobar lo suyo, y nadie debería aflojar eso sin
 * darse cuenta.
 *
 * <p>Se prueba sobre {@link Permisos}, que es donde vive la decisión, y no
 * montando una orden de compra entera: lo que cambió es <i>a quién se le
 * concede la excepción</i>, y eso se decide ahí. La autenticación se pone a
 * mano en el {@code SecurityContextHolder}, igual que hace
 * {@code JwtAuthenticationFilter} y que las demás pruebas de permisos del
 * proyecto — sin añadir {@code spring-security-test}, que el proyecto no usa.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F64 · quién puede aprobar su propia orden de compra")
class AprobacionOrdenCompraTest {

    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    /** Deja al usuario en curso autenticado con las authorities que se le pasen. */
    private void entrarComo(String correo, String... authorities) {
        var lista = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .map(a -> (org.springframework.security.core.GrantedAuthority) a)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(correo, null, lista));
    }

    @Test
    @DisplayName("el Administrador queda exento: puede aprobar la orden que él creó")
    void elAdministradorEstaExento() {
        entrarComo("admin@marathon.com", "ROLE_ADMINISTRADOR", "compras:aprobar");

        assertThat(Permisos.esAdministrador())
                .as("es la condición que abre la excepción a la separación de funciones")
                .isTrue();
    }

    @Test
    @DisplayName("el Encargado de Compras NO queda exento, aunque le den el permiso de aprobar")
    void elEncargadoDeComprasNoEstaExento() {
        // El caso que hay que vigilar: alguien concede 'compras:aprobar' a
        // Compras desde la pantalla de roles. Gana el permiso de aprobar, pero
        // NO gana la exención — sigue sin poder aprobar lo suyo.
        entrarComo("compras@marathon.com", "ROLE_ENCARGADO DE COMPRAS", "compras:aprobar");

        assertThat(Permisos.tiene("compras:aprobar"))
                .as("tiene el permiso...")
                .isTrue();
        assertThat(Permisos.esAdministrador())
                .as("...pero la exención mira el ROL, no el permiso, justo para que "
                    + "no se pueda regalar marcando una casilla")
                .isFalse();
    }

    @Test
    @DisplayName("ningún otro rol queda exento")
    void losDemasRolesNoEstanExentos() {
        for (String rol : List.of("ROLE_SUPERVISOR E-COMMERCE", "ROLE_OPERADOR DE BODEGA",
                                  "ROLE_OPERADOR DE PEDIDOS", "ROLE_ENCARGADO DE PRODUCCIÓN")) {
            SecurityContextHolder.clearContext();
            entrarComo("alguien@marathon.com", rol);
            assertThat(Permisos.esAdministrador())
                    .as("la exención es solo del Administrador, y " + rol + " no lo es")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("sin sesión no se concede la exención")
    void sinSesionNoHayExencion() {
        SecurityContextHolder.clearContext();

        assertThat(Permisos.esAdministrador())
                .as("el arnés llama a los servicios sin contexto de seguridad; "
                    + "ante la duda, no se regala la excepción")
                .isFalse();
    }

    @Test
    @DisplayName("y sigue habiendo un solo rol con 'compras:aprobar' en la matriz")
    void soloUnRolAprueba() {
        // Si esto cambia algún día, que sea a propósito: quien lo cambie debería
        // leer antes por qué la exención pregunta por el rol y no por el permiso.
        List<String> roles = jdbc.queryForList(
                "SELECT r.nombre FROM rol r "
                + "JOIN rol_permiso rp ON rp.id_rol = r.id_rol "
                + "JOIN permiso p ON p.id_permiso = rp.id_permiso "
                + "WHERE p.modulo = 'compras' AND p.accion = 'aprobar' "
                + "ORDER BY r.nombre", String.class);

        assertThat(roles).containsExactly("Administrador");
    }
}
