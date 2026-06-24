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
import com.marathon.dto.auth.LoginRequestDTO;
import com.marathon.dto.auth.LoginResponseDTO;
import com.marathon.dto.auth.RefreshTokenRequestDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.Usuario;
import com.marathon.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioDetailsService usuarioDetailsService;
    private final JwtUtils jwtUtils;
    private final UsuarioRepository usuarioRepository;
    private final LogService logService;
    private final HttpServletRequest httpServletRequest;

    public AuthService(AuthenticationManager authenticationManager,
                       UsuarioDetailsService usuarioDetailsService,
                       JwtUtils jwtUtils,
                       UsuarioRepository usuarioRepository,
                       LogService logService,
                       HttpServletRequest httpServletRequest) {
        this.authenticationManager = authenticationManager;
        this.usuarioDetailsService = usuarioDetailsService;
        this.jwtUtils = jwtUtils;
        this.usuarioRepository = usuarioRepository;
        this.logService = logService;
        this.httpServletRequest = httpServletRequest;
    }

    public LoginResponseDTO login(LoginRequestDTO request) {
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

            logService.registrar(usuario.getIdUsuario(), "auth", "login",
                    "Login exitoso: " + usuario.getCorreo(), httpServletRequest.getRemoteAddr());

            return new LoginResponseDTO(
                    token,
                    refreshToken,
                    usuario.getIdUsuario(),
                    usuario.getNombre(),
                    usuario.getApellido(),
                    usuario.getCorreo(),
                    roles.isEmpty() ? "" : roles.get(0),
                    permisos
            );

        } catch (DisabledException e) {
            throw new ValidationException("Usuario inactivo");
        } catch (BadCredentialsException e) {
            throw new ValidationException("Credenciales incorrectas");
        }
    }

    public LoginResponseDTO refresh(RefreshTokenRequestDTO request) {
        String refreshToken = request.getRefreshToken();

        try {
            String email = jwtUtils.extractUsername(refreshToken);

            if (email == null || jwtUtils.isTokenExpired(refreshToken)) {
                throw new ValidationException("Refresh token inválido o expirado");
            }

            UserDetails userDetails = usuarioDetailsService.loadUserByUsername(email);
            Usuario usuario = (Usuario) userDetails;

            if (!jwtUtils.isTokenValid(refreshToken, userDetails)) {
                throw new ValidationException("Refresh token inválido");
            }

            List<String> roles = usuarioDetailsService.getRoles(usuario.getIdUsuario());
            List<String> permisos = usuarioDetailsService.getPermisos(usuario.getIdUsuario());

            String newToken = jwtUtils.generateToken(usuario, roles, permisos);
            String newRefreshToken = jwtUtils.generateRefreshToken(usuario);

            return new LoginResponseDTO(
                    newToken,
                    newRefreshToken,
                    usuario.getIdUsuario(),
                    usuario.getNombre(),
                    usuario.getApellido(),
                    usuario.getCorreo(),
                    roles.isEmpty() ? "" : roles.get(0),
                    permisos
            );

        } catch (Exception e) {
            if (e instanceof ValidationException) throw e;
            throw new ValidationException("Refresh token inválido o expirado");
        }
    }
}
