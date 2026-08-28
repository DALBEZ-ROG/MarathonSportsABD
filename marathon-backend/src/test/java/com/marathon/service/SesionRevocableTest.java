package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.config.JwtUtils;
import com.marathon.model.TokenRevocado;
import com.marathon.repository.TokenRevocadoRepository;
import com.marathon.repository.UsuarioRepository;

/**
 * F60 (D-23) — cerrar sesión cierra la sesión.
 *
 * <p>El defecto era de los que dan vergüenza al leerlos: {@code /api/auth/logout}
 * devolvía «Sesión cerrada correctamente» y <b>no hacía nada</b>. El token seguía
 * valiendo hasta caducar por su cuenta. La mitigación anterior fue bajar esa
 * ventana de 24 h a 2 h, que es acortar el problema, no resolverlo.
 *
 * <p>Lo que se fija aquí es el mecanismo: cada token lleva un identificador
 * único, revocarlo lo apunta en una lista, y esa lista se consulta. Lo que se
 * fija en el navegador —que la cookie deje de servir después del logout— está
 * comprobado sobre la aplicación en marcha, porque el arnés de pruebas no pasa
 * por el filtro HTTP.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F60 · D-23 · la sesión se puede cerrar de verdad")
class SesionRevocableTest {

    @Autowired private JwtUtils jwtUtils;
    @Autowired private TokenRevocadoService tokenRevocadoService;
    @Autowired private TokenRevocadoRepository tokenRevocadoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JdbcTemplate jdbc;

    private static final String MARCA = "__prueba__";

    @AfterEach
    void limpiar() {
        jdbc.update("delete from token_revocado where correo like ? or correo = ?",
                MARCA + "%", "admin@marathon.com");
    }

    private String tokenDeAdmin() {
        var admin = usuarioRepository.findByCorreo("admin@marathon.com")
                .orElseThrow(() -> new IllegalStateException("falta admin@marathon.com"));
        return jwtUtils.generateToken(admin, List.of("Administrador"), List.of("dashboard:ver"));
    }

    @Test
    @DisplayName("todo token firmado lleva un jti con el que poder nombrarlo")
    void todoTokenLlevaJti() {
        String token = tokenDeAdmin();

        assertThat(jwtUtils.extractJti(token))
                .as("sin jti no hay forma de revocar un token concreto: "
                    + "habría que guardar el token entero, o tirar todas las sesiones a la vez")
                .isNotNull()
                .isNotBlank();
    }

    @Test
    @DisplayName("dos tokens del mismo usuario tienen jti distintos")
    void cadaTokenTieneSuPropioJti() {
        // Si compartieran jti, cerrar sesión en el móvil cerraría también la del
        // ordenador. Son sesiones distintas y se revocan por separado.
        assertThat(jwtUtils.extractJti(tokenDeAdmin()))
                .isNotEqualTo(jwtUtils.extractJti(tokenDeAdmin()));
    }

    @Test
    @DisplayName("revocar un token lo deja marcado; el resto no se toca")
    void revocarSoloAfectaAlTokenRevocado() {
        String revocado = tokenDeAdmin();
        String intacto = tokenDeAdmin();

        assertThat(tokenRevocadoService.estaRevocado(jwtUtils.extractJti(revocado))).isFalse();

        assertThat(tokenRevocadoService.revocar(revocado, "acceso")).isTrue();

        assertThat(tokenRevocadoService.estaRevocado(jwtUtils.extractJti(revocado)))
                .as("es lo que hacía falta para que el logout signifique algo")
                .isTrue();
        assertThat(tokenRevocadoService.estaRevocado(jwtUtils.extractJti(intacto)))
                .as("cerrar una sesión no puede cerrar las demás")
                .isFalse();
    }

    @Test
    @DisplayName("cerrar sesión dos veces con el mismo token no revienta")
    void revocarEsIdempotente() {
        String token = tokenDeAdmin();

        assertThat(tokenRevocadoService.revocar(token, "acceso")).isTrue();
        assertThat(tokenRevocadoService.revocar(token, "acceso"))
                .as("el segundo intento chocaría con la clave primaria si no se comprobara antes")
                .isTrue();

        assertThat(tokenRevocadoRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("un token ilegible no impide cerrar sesión")
    void unTokenBasuraNoRompeElLogout() {
        // Cerrar sesión NUNCA debe fallar: dejar a alguien sin poder salir sería
        // peor que el defecto que esto arregla.
        assertThat(tokenRevocadoService.revocar("esto-no-es-un-jwt", "acceso")).isFalse();
        assertThat(tokenRevocadoService.revocar(null, "acceso")).isFalse();
        assertThat(tokenRevocadoService.revocar("", "acceso")).isFalse();
    }

    @Test
    @DisplayName("la purga se lleva lo caducado y respeta lo vivo")
    void laPurgaSoloTocaLoCaducado() {
        String vivo = tokenDeAdmin();
        tokenRevocadoService.revocar(vivo, "acceso");

        // Una revocación que ya no pinta nada: su token habría caducado ayer.
        tokenRevocadoRepository.save(new TokenRevocado(
                "caducado-de-prueba", MARCA + "viejo@marathon.com", "acceso",
                LocalDateTime.now().minusDays(1)));

        assertThat(tokenRevocadoService.purgarExpirados()).isEqualTo(1);

        assertThat(tokenRevocadoRepository.existsById("caducado-de-prueba"))
                .as("sin purga la lista crecería una fila por cada cierre de sesión, para siempre")
                .isFalse();
        assertThat(tokenRevocadoService.estaRevocado(jwtUtils.extractJti(vivo)))
                .as("la purga no puede resucitar una sesión cerrada que aún no ha caducado")
                .isTrue();
    }
}
