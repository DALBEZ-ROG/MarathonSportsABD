package com.marathon.config;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.marathon.service.UsuarioDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UsuarioDetailsService usuarioDetailsService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, UsuarioDetailsService usuarioDetailsService) {
        this.jwtUtils = jwtUtils;
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

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
