package com.marathon.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.marathon.model.RolPermiso;
import com.marathon.model.Usuario;
import com.marathon.model.UsuarioRol;
import com.marathon.repository.RolPermisoRepository;
import com.marathon.repository.UsuarioRepository;
import com.marathon.repository.UsuarioRolRepository;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final RolPermisoRepository rolPermisoRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository,
                                 UsuarioRolRepository usuarioRolRepository,
                                 RolPermisoRepository rolPermisoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.rolPermisoRepository = rolPermisoRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreoAndEstado(correo, "activo")
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con correo: " + correo));

        List<UsuarioRol> usuarioRoles = usuarioRolRepository.findByUsuarioIdUsuario(usuario.getIdUsuario());

        List<GrantedAuthority> authorities = new ArrayList<>();

        for (UsuarioRol ur : usuarioRoles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + ur.getRol().getNombre().toUpperCase()));

            List<RolPermiso> rolPermisos = rolPermisoRepository.findByRolIdRol(ur.getRol().getIdRol());
            for (RolPermiso rp : rolPermisos) {
                String permisoStr = rp.getPermiso().getModulo() + ":" + rp.getPermiso().getAccion();
                authorities.add(new SimpleGrantedAuthority(permisoStr));
            }
        }

        usuario.setAuthorities(authorities);
        return usuario;
    }

    public List<String> getRoles(Integer idUsuario) {
        List<UsuarioRol> usuarioRoles = usuarioRolRepository.findByUsuarioIdUsuario(idUsuario);
        List<String> roles = new ArrayList<>();
        for (UsuarioRol ur : usuarioRoles) {
            roles.add(ur.getRol().getNombre());
        }
        return roles;
    }

    public List<String> getPermisos(Integer idUsuario) {
        List<UsuarioRol> usuarioRoles = usuarioRolRepository.findByUsuarioIdUsuario(idUsuario);
        List<String> permisos = new ArrayList<>();
        for (UsuarioRol ur : usuarioRoles) {
            List<RolPermiso> rolPermisos = rolPermisoRepository.findByRolIdRol(ur.getRol().getIdRol());
            for (RolPermiso rp : rolPermisos) {
                String permiso = rp.getPermiso().getModulo() + ":" + rp.getPermiso().getAccion();
                if (!permisos.contains(permiso)) {
                    permisos.add(permiso);
                }
            }
        }
        return permisos;
    }
}
