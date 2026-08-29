package com.marathon.service;

import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.marathon.config.JwtUtils;
import com.marathon.config.LimitadorDeIntentos;
import com.marathon.dto.auth.LoginRequestDTO;
import com.marathon.dto.auth.LoginResponseDTO;
import com.marathon.dto.auth.RefreshTokenRequestDTO;
import com.marathon.exception.DemasiadosIntentosException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Usuario;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioDetailsService usuarioDetailsService;
    private final JwtUtils jwtUtils;
    private final LogService logService;
    private final HttpServletRequest httpServletRequest;
    private final LimitadorDeIntentos limitador;
    private final TokenRevocadoService tokenRevocadoService;

    public AuthService(AuthenticationManager authenticationManager,
                       UsuarioDetailsService usuarioDetailsService,
                       JwtUtils jwtUtils,
                       LogService logService,
                       HttpServletRequest httpServletRequest,
                       LimitadorDeIntentos limitador,
                       TokenRevocadoService tokenRevocadoService) {
        this.authenticationManager = authenticationManager;
        this.usuarioDetailsService = usuarioDetailsService;
        this.jwtUtils = jwtUtils;
        this.logService = logService;
        this.httpServletRequest = httpServletRequest;
        this.limitador = limitador;
        this.tokenRevocadoService = tokenRevocadoService;
    }

    public LoginResponseDTO login(LoginRequestDTO request) {
        String ip = httpServletRequest.getRemoteAddr();

        // L10 (D-25): sin esto se podian probar contrasenas sin limite.
        if (!limitador.permitido(request.getCorreo(), ip)) {
            logService.registrar(null, "auth", "login_bloqueado",
                    "Demasiados intentos fallidos para " + request.getCorreo(), ip);
            throw new DemasiadosIntentosException("Demasiados intentos fallidos. "
                    + "Espera " + limitador.minutosDeBloqueo() + " minutos e inténtalo de nuevo.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getCorreo(),
                            request.getPassword()
                    )
            );

            Usuario usuario = (Usuario) authentication.getPrincipal();
            List<String> roles = usuarioDetailsService.getRoles(usuario.getIdUsuario());
            List<String> permisos = usuarioDetailsService.getPermisos(usuario.getIdUsuario());

            String token = jwtUtils.generateToken(usuario, roles, permisos);
            String refreshToken = jwtUtils.generateRefreshToken(usuario);

            limitador.registrarExito(request.getCorreo(), ip);

            logService.registrar(usuario.getIdUsuario(), "auth", "login",
                    "Login exitoso: " + usuario.getCorreo(), ip);

            LoginResponseDTO respuesta = new LoginResponseDTO(
                    token,
                    refreshToken,
                    usuario.getIdUsuario(),
                    usuario.getNombre(),
                    usuario.getApellido(),
                    usuario.getCorreo(),
                    roles.isEmpty() ? "" : roles.get(0),
                    permisos
            );

            // F60 (D-27): el front ya no puede leer el token para saber cuando
            // caduca la sesion, asi que se le dice.
            respuesta.setExpiraEn(jwtUtils.extractExpiration(token).getTime());
            return respuesta;

        } catch (DisabledException | BadCredentialsException e) {
            // L10 (D-25): el MISMO mensaje en los dos casos.
            //
            // Antes, "Usuario inactivo" y "Credenciales incorrectas" eran
            // respuestas distintas, y esa diferencia dice si un correo
            // corresponde a una cuenta real: sirve para hacer inventario de
            // usuarios antes de empezar a probar contrasenas.
            limitador.registrarFallo(request.getCorreo(), ip);
            throw new ValidationException("Correo o contraseña incorrectos");
        }
    }

    public LoginResponseDTO refresh(RefreshTokenRequestDTO request) {
        String refreshToken = request.getRefreshToken();

        try {
            String email = jwtUtils.extractUsername(refreshToken);

            if (email == null || jwtUtils.isTokenExpired(refreshToken)) {
                throw new ValidationException("Refresh token inválido o expirado");
            }

            // F60 (D-23): sin esto, cerrar sesion invalidaba el token de acceso
            // pero NO el de refresco, y con el refresco se saca uno de acceso
            // nuevo. Es decir: el logout no habria cerrado nada, solo obligado a
            // dar un rodeo de una peticion.
            if (tokenRevocadoService.estaRevocado(jwtUtils.extractJti(refreshToken))) {
                throw new ValidationException("La sesión fue cerrada. Vuelve a entrar.");
            }

            UserDetails userDetails = usuarioDetailsService.loadUserByUsername(email);
            Usuario usuario = (Usuario) userDetails;

            // L7 (D-05): sin esto, un usuario desactivado seguia renovando su
            // token cada 24 h mientras lo hiciera dentro de la ventana de 7 dias
            // del refresh, es decir: para siempre.
            if (!userDetails.isEnabled()) {
                throw new ValidationException("Usuario inactivo");
            }

            if (!jwtUtils.isTokenValid(refreshToken, userDetails)) {
                throw new ValidationException("Refresh token inválido");
            }

            List<String> roles = usuarioDetailsService.getRoles(usuario.getIdUsuario());
            List<String> permisos = usuarioDetailsService.getPermisos(usuario.getIdUsuario());

            String newToken = jwtUtils.generateToken(usuario, roles, permisos);
            String newRefreshToken = jwtUtils.generateRefreshToken(usuario);

            LoginResponseDTO respuesta = new LoginResponseDTO(
                    newToken,
                    newRefreshToken,
                    usuario.getIdUsuario(),
                    usuario.getNombre(),
                    usuario.getApellido(),
                    usuario.getCorreo(),
                    roles.isEmpty() ? "" : roles.get(0),
                    permisos
            );

            // F60 (D-27): el front ya no puede leer el token para saber cuando
            // caduca la sesion, asi que se le dice.
            respuesta.setExpiraEn(jwtUtils.extractExpiration(newToken).getTime());
            return respuesta;

        } catch (Exception e) {
            if (e instanceof ValidationException) throw e;
            throw new ValidationException("Refresh token inválido o expirado");
        }
    }
}
