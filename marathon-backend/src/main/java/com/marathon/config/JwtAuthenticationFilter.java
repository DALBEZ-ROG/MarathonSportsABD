package com.marathon.config;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.marathon.service.TokenRevocadoService;
import com.marathon.service.UsuarioDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UsuarioDetailsService usuarioDetailsService;

    private final TokenRevocadoService tokenRevocadoService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils,
                                   UsuarioDetailsService usuarioDetailsService,
                                   TokenRevocadoService tokenRevocadoService) {
        this.jwtUtils = jwtUtils;
        this.usuarioDetailsService = usuarioDetailsService;
        this.tokenRevocadoService = tokenRevocadoService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // ------------------------------------------------------------------
        // F60 (D-27): la cookie manda; la cabecera sigue valiendo.
        // ------------------------------------------------------------------
        // El navegador manda una cookie HttpOnly que su propio JavaScript no
        // puede leer: ahi esta el cierre de D-27. La cabecera Authorization se
        // conserva a proposito, y no reabre el agujero — quien la usa son los
        // clientes que NO son un navegador (fase37_pruebas_endpoints.ps1, curl,
        // Swagger), donde no hay localStorage que robar. Lo que importaba de
        // D-27 era que el token dejara de estar guardado donde un XSS puede
        // leerlo, y ya no lo esta.
        String jwt = CookieSesion.leer(request, CookieSesion.COOKIE_ACCESO);

        if (jwt == null) {
            final String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }
            jwt = authHeader.substring(7);
        }

        try {
            final String userEmail = jwtUtils.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = usuarioDetailsService.loadUserByUsername(userEmail);

                // ------------------------------------------------------------
                // L7 (D-05): un usuario dado de baja deja de entrar.
                // ------------------------------------------------------------
                // isTokenValid solo comprueba que el nombre coincida y que el
                // token no haya expirado. Usuario.isEnabled() existia desde el
                // principio y no lo llamaba nadie, asi que desactivar a alguien
                // no le quitaba el acceso: seguia operando hasta 24 h con su
                // token, y como el refresh tampoco lo comprobaba, podia
                // renovarlo indefinidamente. UsuarioService.eliminar() —la unica
                // via documentada para retirar el acceso— no retiraba el acceso.
                if (!userDetails.isEnabled()) {
                    logger.debug("Token de un usuario inactivo: " + userEmail);
                    filterChain.doFilter(request, response);
                    return;
                }

                // ------------------------------------------------------------
                // F60 (D-23): una sesion cerrada esta cerrada de verdad.
                // ------------------------------------------------------------
                // Hasta aqui, /api/auth/logout devolvia "Sesion cerrada
                // correctamente" y no invalidaba nada: el token seguia
                // sirviendo hasta caducar. Ahora cada peticion pregunta si ese
                // jti concreto fue revocado. Es una consulta por clave primaria
                // por peticion — el costo real de tener revocacion, y el motivo
                // por el que antes se habia descartado.
                if (tokenRevocadoService.estaRevocado(jwtUtils.extractJti(jwt))) {
                    logger.debug("Token revocado (sesion cerrada): " + userEmail);
                    filterChain.doFilter(request, response);
                    return;
                }

                if (jwtUtils.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token inválido o expirado — continuar sin autenticar
            logger.debug("JWT validation failed: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
