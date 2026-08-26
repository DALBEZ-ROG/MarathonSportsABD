package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.auth.RefreshTokenRequestDTO;
import com.marathon.dto.usuario.UsuarioCambiarPasswordDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Usuario;
import com.marathon.repository.UsuarioRepository;

/**
 * L7 — desactivar a un usuario le retira el acceso de verdad (D-05) y nadie
 * puede operar sobre la cuenta de otro (D-09).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L7 - el acceso se puede retirar")
class AccesoRevocableTest {

    @Autowired private AuthService authService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioDetailsService usuarioDetailsService;
    @Autowired private com.marathon.config.JwtUtils jwtUtils;

    private Usuario bodega;
    private String estadoOriginal;

    @BeforeEach
    void prepararDatos() {
        bodega = usuarioRepository.findByCorreo("bodega@marathon.com").orElseThrow();
        estadoOriginal = bodega.getEstado();
    }

    @AfterEach
    void restaurar() {
        SecurityContextHolder.clearContext();
        bodega.setEstado(estadoOriginal);
        usuarioRepository.save(bodega);
    }

    /** Deja a `usuario` como el autenticado de la peticion en curso. */
    private void autenticarComo(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
    }

    // ---------------------------------------------------------------- D-05 ---

    @Test
    @DisplayName("el refresh de un usuario desactivado se rechaza")
    void elRefreshDeUnUsuarioDesactivadoSeRechaza() {
        String refresh = jwtUtils.generateRefreshToken(bodega);

        // Con el usuario activo, el refresh funciona.
        assertThat(authService.refresh(peticion(refresh)).getToken()).isNotBlank();

        bodega.setEstado("inactivo");
        usuarioRepository.save(bodega);

        // Antes de la L7 esto devolvia un token nuevo de 24 h: el usuario dado de
        // baja podia renovar indefinidamente mientras lo hiciera dentro de los
        // 7 dias del refresh.
        assertThatThrownBy(() -> authService.refresh(peticion(refresh)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Usuario.isEnabled() refleja el estado, y ahora alguien lo mira")
    void isEnabledRefleljaElEstado() {
        assertThat(bodega.isEnabled()).isTrue();
        bodega.setEstado("inactivo");
        assertThat(bodega.isEnabled()).isFalse();
    }

    private RefreshTokenRequestDTO peticion(String token) {
        RefreshTokenRequestDTO dto = new RefreshTokenRequestDTO();
        dto.setRefreshToken(token);
        return dto;
    }

    // ---------------------------------------------------------------- D-09 ---

    @Test
    @DisplayName("no se puede cambiar la contrasena de otra cuenta")
    void noSePuedeCambiarLaPasswordDeOtro() {
        Usuario pedidos = usuarioRepository.findByCorreo("pedidos@marathon.com").orElseThrow();
        autenticarComo(bodega);   // Operador de Bodega, no administrador

        UsuarioCambiarPasswordDTO dto = new UsuarioCambiarPasswordDTO();
        dto.setPasswordActual("Demo1234!");     // la correcta de la victima
        dto.setPasswordNuevo("Otra1234!");
        dto.setConfirmarPassword("Otra1234!");

        // Antes de la L7 esto devolvia 204 y cambiaba la contrasena ajena.
        assertThatThrownBy(() -> usuarioService.cambiarPassword(pedidos.getIdUsuario(), dto))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("el administrador si puede sobre cualquier cuenta")
    void elAdministradorSiPuede() {
        // Se carga por el mismo camino que usa JwtAuthenticationFilter: es
        // UsuarioDetailsService quien rellena las authorities. Cargarlo por el
        // repositorio da una entidad sin roles, y la prueba no probaria nada.
        Usuario admin = (Usuario) usuarioDetailsService.loadUserByUsername("admin@marathon.com");
        autenticarComo(admin);

        UsuarioCambiarPasswordDTO dto = new UsuarioCambiarPasswordDTO();
        dto.setPasswordActual("no-es-la-correcta");
        dto.setPasswordNuevo("Otra1234!");
        dto.setConfirmarPassword("Otra1234!");

        // Pasa el control de propiedad y falla mas adelante, en la contrasena
        // actual: es la prueba de que el administrador no queda bloqueado por
        // el control nuevo.
        assertThatThrownBy(() -> usuarioService.cambiarPassword(bodega.getIdUsuario(), dto))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("contraseña actual");
    }

    @Test
    @DisplayName("sobre la cuenta propia si se puede")
    void sobreLaCuentaPropiaSiSePuede() {
        autenticarComo(bodega);

        UsuarioCambiarPasswordDTO dto = new UsuarioCambiarPasswordDTO();
        dto.setPasswordActual("no-es-la-correcta");
        dto.setPasswordNuevo("Otra1234!");
        dto.setConfirmarPassword("Otra1234!");

        assertThatThrownBy(() -> usuarioService.cambiarPassword(bodega.getIdUsuario(), dto))
                .as("no debe ser AccessDenied: la cuenta es suya")
                .isInstanceOf(ValidationException.class);
    }
}
