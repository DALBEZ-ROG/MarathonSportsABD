package com.marathon.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.marathon.model.Usuario;
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

    /**
     * F94 — DOS consultas, no ciento y pico.
     *
     * <p>Esto corre en CADA peticion del sistema: el filtro JWT resuelve el
     * token contra la base para que un cambio de permisos surta efecto sin
     * volver a entrar (F48). Lo que hacia antes era recorrer los roles y, por
     * cada uno, sus {@code RolPermiso}, leyendo {@code rp.getPermiso()} fila a
     * fila. La relacion es EAGER, pero EAGER sin {@code JOIN FETCH} no junta
     * nada: Hibernate lanzaba <b>un SELECT por permiso</b>.
     *
     * <p>Medido con la cuenta de administrador (99 permisos): una sola carga de
     * una pantalla disparaba <b>99 consultas a `permiso`</b> antes de empezar a
     * hacer el trabajo que se le habia pedido. Y eso lastraba todas las
     * pantallas por igual, que es por lo que «todo» iba lento y no una parte.
     *
     * <p>Se conserva intacto lo importante: se sigue consultando la base en cada
     * peticion, asi que quitarle un permiso a alguien le surte efecto en la
     * siguiente llamada. Lo que cambia es cuanto cuesta averiguarlo.
     */
    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreoAndEstado(correo, "activo")
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con correo: " + correo));

        List<GrantedAuthority> authorities = new ArrayList<>();

        for (String rol : usuarioRolRepository.nombresDeRol(usuario.getIdUsuario())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()));
        }
        for (String permiso : rolPermisoRepository.permisosDeUsuario(usuario.getIdUsuario())) {
            authorities.add(new SimpleGrantedAuthority(permiso));
        }

        usuario.setAuthorities(authorities);
        return usuario;
    }

    public List<String> getRoles(Integer idUsuario) {
        return usuarioRolRepository.nombresDeRol(idUsuario);
    }

    public List<String> getPermisos(Integer idUsuario) {
        // El DISTINCT lo hace la base. El bucle anterior usaba
        // `permisos.contains(...)` dentro del propio bucle, que ademas de una
        // consulta por permiso era una comparacion cuadratica sobre una lista.
        return rolPermisoRepository.permisosDeUsuario(idUsuario);
    }
}
