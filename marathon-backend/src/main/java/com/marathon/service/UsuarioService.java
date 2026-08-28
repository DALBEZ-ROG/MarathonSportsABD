package com.marathon.service;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.rol.RolResponseDTO;
import com.marathon.dto.usuario.*;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Rol;
import com.marathon.model.Usuario;
import com.marathon.model.UsuarioRol;
import com.marathon.repository.RolRepository;
import com.marathon.repository.UsuarioRepository;
import com.marathon.repository.UsuarioRolRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final RolRepository rolRepository;
    private final RolService rolService;
    private final PasswordEncoder passwordEncoder;
    private final LogService logService;


    public UsuarioService(UsuarioRepository usuarioRepository, UsuarioRolRepository usuarioRolRepository,
                          RolRepository rolRepository, RolService rolService, PasswordEncoder passwordEncoder,
                          LogService logService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.rolRepository = rolRepository;
        this.rolService = rolService;
        this.passwordEncoder = passwordEncoder;
        this.logService = logService;
    }

    public PageResponseDTO<UsuarioResponseDTO> listar(int page, int size, String nombre, String estado) {
        // F51 (D-41): el ORDEN es obligatorio en una lista paginada.
        // Sin ORDER BY, PostgreSQL devuelve las filas en el orden del monton, y
        // un UPDATE reescribe la fila al final: la que acabas de editar
        // desaparece de su pagina. Ademas, dos paginas consecutivas pueden
        // repetir una fila y esconder otra.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "idUsuario"));
        Page<Usuario> result;

        if (nombre != null && !nombre.isEmpty() && estado != null && !estado.isEmpty()) {
            result = usuarioRepository.findByNombreOrApellidoAndEstado(nombre, estado, pageable);
        } else if (nombre != null && !nombre.isEmpty()) {
            result = usuarioRepository.findByNombreOrApellido(nombre, pageable);
        } else if (estado != null && !estado.isEmpty()) {
            result = usuarioRepository.findByEstado(estado, pageable);
        } else {
            result = usuarioRepository.findAll(pageable);
        }

        List<UsuarioResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public UsuarioResponseDTO obtener(Integer id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        return toDTO(usuario);
    }

    @Transactional
    public UsuarioResponseDTO crear(UsuarioRequestDTO dto) {
        Optional<Usuario> existente = usuarioRepository.findByCorreo(dto.getCorreo());
        if (existente.isPresent()) {
            throw new ValidationException("Ya existe un usuario con ese correo");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(dto.getNombre());
        usuario.setApellido(dto.getApellido());
        usuario.setCorreo(dto.getCorreo());
        usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        usuario.setEstado(dto.getEstado() != null ? dto.getEstado() : "activo");
        usuario = usuarioRepository.save(usuario);

        for (Integer idRol : dto.getIdRoles()) {
            Rol rol = rolRepository.findById(idRol)
                    .orElseThrow(() -> new ResourceNotFoundException("Rol", idRol));
            usuarioRolRepository.save(new UsuarioRol(usuario, rol));
        }

        logService.registrar(null, "usuarios", "crear",
                "Usuario creado: " + usuario.getCorreo(), null);

        return toDTO(usuario);
    }

    @Transactional
    public UsuarioResponseDTO actualizar(Integer id, UsuarioUpdateDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        Optional<Usuario> existente = usuarioRepository.findByCorreo(dto.getCorreo());
        if (existente.isPresent() && !existente.get().getIdUsuario().equals(id)) {
            throw new ValidationException("Ya existe un usuario con ese correo");
        }

        usuario.setNombre(dto.getNombre());
        usuario.setApellido(dto.getApellido());
        usuario.setCorreo(dto.getCorreo());
        if (dto.getEstado() != null) {
            usuario.setEstado(dto.getEstado());
        }
        usuarioRepository.save(usuario);

        // Reasignar roles
        List<UsuarioRol> rolesActuales = usuarioRolRepository.findByUsuarioIdUsuario(id);
        usuarioRolRepository.deleteAll(rolesActuales);

        for (Integer idRol : dto.getIdRoles()) {
            Rol rol = rolRepository.findById(idRol)
                    .orElseThrow(() -> new ResourceNotFoundException("Rol", idRol));
            usuarioRolRepository.save(new UsuarioRol(usuario, rol));
        }

        return toDTO(usuario);
    }

    @Transactional
    public void cambiarPassword(Integer id, UsuarioCambiarPasswordDTO dto) {
        // --------------------------------------------------------------------
        // L7 (D-09): solo la cuenta propia, salvo que quien llame sea Admin.
        // --------------------------------------------------------------------
        // La regla de SecurityConfig para PUT /api/usuarios/*/password es
        // .authenticated(), y esta declarada ANTES de la que reserva
        // /api/usuarios/** al Administrador. El comentario del controlador decia
        // "sobre su propia cuenta", pero nadie comparaba el id con el del
        // usuario autenticado. No era una toma de control directa —hace falta
        // acertar la contrasena actual de la victima— pero dejaba un oraculo
        // para adivinarla contra CUALQUIER cuenta, con intentos ilimitados y
        // respuestas distinguibles.
        if (!esElUsuarioAutenticado(id) && !esAdministrador()) {
            throw new AccessDeniedException("Solo puedes cambiar tu propia contraseña");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        if (!passwordEncoder.matches(dto.getPasswordActual(), usuario.getPassword())) {
            throw new ValidationException("La contraseña actual es incorrecta");
        }

        if (!dto.getPasswordNuevo().equals(dto.getConfirmarPassword())) {
            throw new ValidationException("Las contraseñas no coinciden");
        }

        usuario.setPassword(passwordEncoder.encode(dto.getPasswordNuevo()));
        usuarioRepository.save(usuario);
    }

    @Transactional   // L11 (D-16): misma razon que en ProductoService.
    public void eliminar(Integer id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        // L14 (D-31): antes se identificaba al administrador protegido por su
        // correo escrito a mano ("admin@marathon.com"). Cambiarle el correo
        // desactivaba la proteccion sin avisar. Lo que de verdad hay que
        // garantizar no es que sobreviva UNA cuenta concreta, sino que quede al
        // menos un administrador activo con el que poder entrar.
        if (esElUltimoAdministradorActivo(usuario)) {
            throw new ValidationException("No se puede desactivar: es el único administrador activo. "
                    + "Crea o activa otro administrador antes de dar de baja a este.");
        }

        usuario.setEstado("inactivo");
        usuarioRepository.save(usuario);

        logService.registrar(null, "usuarios", "desactivar",
                "Usuario desactivado: " + usuario.getCorreo(), null);
    }

    /**
     * ¿Dar de baja a este usuario dejaria el sistema sin ningun administrador
     * activo? (L14, D-31)
     */
    private boolean esElUltimoAdministradorActivo(Usuario usuario) {
        boolean esAdmin = usuarioRolRepository.findByUsuarioIdUsuario(usuario.getIdUsuario()).stream()
                .anyMatch(ur -> ur.getRol() != null && "Administrador".equals(ur.getRol().getNombre()));
        if (!esAdmin || !"activo".equals(usuario.getEstado())) {
            return false;
        }
        return usuarioRolRepository.contarAdministradoresActivos() <= 1;
    }

    /** ¿El id que se quiere tocar es el del usuario que hizo la peticion? */
    private boolean esElUsuarioAutenticado(Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof Usuario u
                && u.getIdUsuario() != null
                && u.getIdUsuario().equals(id);
    }

    private boolean esAdministrador() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMINISTRADOR".equals(a.getAuthority()));
    }

    private UsuarioResponseDTO toDTO(Usuario usuario) {
        UsuarioResponseDTO dto = new UsuarioResponseDTO();
        dto.setIdUsuario(usuario.getIdUsuario());
        dto.setNombre(usuario.getNombre());
        dto.setApellido(usuario.getApellido());
        dto.setCorreo(usuario.getCorreo());
        dto.setEstado(usuario.getEstado());
        dto.setCreatedAt(usuario.getCreatedAt());
        dto.setUpdatedAt(usuario.getUpdatedAt());

        List<UsuarioRol> usuarioRoles = usuarioRolRepository.findByUsuarioIdUsuario(usuario.getIdUsuario());
        List<RolResponseDTO> roles = usuarioRoles.stream()
                .map(ur -> rolService.toDTO(ur.getRol()))
                .collect(Collectors.toList());
        dto.setRoles(roles);

        return dto;
    }
}
