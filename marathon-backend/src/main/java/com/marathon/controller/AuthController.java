package com.marathon.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.config.CookieSesion;
import com.marathon.dto.auth.LoginRequestDTO;
import com.marathon.dto.auth.LoginResponseDTO;
import com.marathon.dto.auth.RefreshTokenRequestDTO;
import com.marathon.exception.ValidationException;
import com.marathon.service.AuthService;
import com.marathon.service.TokenRevocadoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Entrada y salida de la sesión.
 *
 * <p><b>F60.</b> Aquí se cierran los dos defectos que quedaban de sesión:
 * <ul>
 *   <li><b>D-27</b> — el token viaja en una cookie {@code HttpOnly} que el
 *       JavaScript del navegador no puede leer, en vez de en {@code localStorage}.
 *   <li><b>D-23</b> — {@code /logout} revoca de verdad. Antes devolvía «Sesión
 *       cerrada correctamente» y no hacía nada en absoluto.
 * </ul>
 *
 * <p>El cuerpo de la respuesta <b>sigue trayendo el token</b>, y no es un
 * descuido: lo usan los clientes que no son un navegador —
 * {@code scripts/fase37_pruebas_endpoints.ps1}, curl, Swagger—, que no tienen
 * dónde guardarlo mal. El navegador lo ignora: desde la F60 el front no lo lee
 * ni lo guarda. Quitarlo del cuerpo rompería esas herramientas sin cerrar nada
 * que la cookie no cierre ya.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieSesion cookieSesion;
    private final TokenRevocadoService tokenRevocadoService;

    public AuthController(AuthService authService,
                          CookieSesion cookieSesion,
                          TokenRevocadoService tokenRevocadoService) {
        this.authService = authService;
        this.cookieSesion = cookieSesion;
        this.tokenRevocadoService = tokenRevocadoService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        LoginResponseDTO response = authService.login(request);
        return conCookies(response);
    }

    /**
     * Renueva la sesión.
     *
     * <p>El refresco se busca primero en la cookie y solo después en el cuerpo,
     * y por eso el cuerpo pasa a ser <b>opcional</b>: desde la F60 el navegador
     * no tiene el token para poder mandarlo. Los clientes que no son navegador
     * siguen mandándolo en el cuerpo, como siempre.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(
            @RequestBody(required = false) RefreshTokenRequestDTO request,
            HttpServletRequest peticion) {

        String refresco = CookieSesion.leer(peticion, CookieSesion.COOKIE_REFRESCO);
        if (refresco == null && request != null) {
            refresco = request.getRefreshToken();
        }
        if (refresco == null || refresco.isBlank()) {
            throw new ValidationException("No hay sesión que renovar");
        }

        LoginResponseDTO response = authService.refresh(new RefreshTokenRequestDTO(refresco));

        // El refresco usado se revoca: si se filtró, ya no vale. Sin esto, un
        // refresco robado sirve 7 días aunque el legítimo lo haya renovado.
        tokenRevocadoService.revocar(refresco, "refresco");

        return conCookies(response);
    }

    /**
     * Cierra la sesión de verdad (D-23).
     *
     * <p>Revoca los dos tokens —el de acceso y el de refresco—, borra las
     * cookies y aprovecha para purgar de la lista lo que ya había caducado.
     *
     * <p><b>Siempre devuelve 200</b>, incluso si no había nada que revocar o el
     * token era ilegible. Cerrar sesión no puede fallar: dejar a alguien sin
     * poder salir sería peor que el defecto que esto arregla.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest peticion) {

        String acceso = CookieSesion.leer(peticion, CookieSesion.COOKIE_ACCESO);
        if (acceso == null) {
            String cabecera = peticion.getHeader("Authorization");
            if (cabecera != null && cabecera.startsWith("Bearer ")) {
                acceso = cabecera.substring(7);
            }
        }
        String refresco = CookieSesion.leer(peticion, CookieSesion.COOKIE_REFRESCO);

        boolean revocado = tokenRevocadoService.revocar(acceso, "acceso");
        tokenRevocadoService.revocar(refresco, "refresco");
        tokenRevocadoService.purgarExpirados();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieSesion.borrarAcceso().toString())
                .header(HttpHeaders.SET_COOKIE, cookieSesion.borrarRefresco().toString())
                .body(Map.of("message", revocado
                        ? "Sesión cerrada correctamente"
                        : "No había ninguna sesión abierta"));
    }

    /** Devuelve la respuesta con la sesión puesta en las dos cookies. */
    private ResponseEntity<LoginResponseDTO> conCookies(LoginResponseDTO response) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieSesion.deAcceso(response.getToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieSesion.deRefresco(response.getRefreshToken()).toString())
                .body(response);
    }
}
