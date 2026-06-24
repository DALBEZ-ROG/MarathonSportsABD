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
import org.springframework.security.crypto.password.PasswordEncoder;
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
        Pageable pageable = PageRequest.of(page, size);
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

    public void cambiarPassword(Integer id, UsuarioCambiarPasswordDTO dto) {
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

    public void eliminar(Integer id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        if ("admin@marathon.com".equals(usuario.getCorreo())) {
            throw new ValidationException("No se puede desactivar al administrador principal");
        }

        usuario.setEstado("inactivo");
        usuarioRepository.save(usuario);

        logService.registrar(null, "usuarios", "desactivar",
                "Usuario desactivado: " + usuario.getCorreo(), null);
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
