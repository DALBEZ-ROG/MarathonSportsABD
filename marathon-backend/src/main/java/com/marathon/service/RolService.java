package com.marathon.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.permiso.PermisoResponseDTO;
import com.marathon.dto.rol.RolRequestDTO;
import com.marathon.dto.rol.RolResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Permiso;
import com.marathon.model.Rol;
import com.marathon.model.RolPermiso;
import com.marathon.repository.PermisoRepository;
import com.marathon.repository.RolPermisoRepository;
import com.marathon.repository.RolRepository;
import com.marathon.repository.UsuarioRolRepository;

@Service
public class RolService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final RolPermisoRepository rolPermisoRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final PermisoService permisoService;

    private final LogService logService;

    public RolService(RolRepository rolRepository, PermisoRepository permisoRepository,
                      RolPermisoRepository rolPermisoRepository, UsuarioRolRepository usuarioRolRepository,
                      PermisoService permisoService,
                  LogService logService) {
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.rolPermisoRepository = rolPermisoRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.permisoService = permisoService;
        this.logService = logService;
    }

    public List<RolResponseDTO> listarTodos() {
        // F51 (D-41): sin orden, la lista de roles se reordenaba sola en cuanto
        // se editaba uno.
        return rolRepository.findAll(Sort.by(Sort.Direction.ASC, "idRol")).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public RolResponseDTO obtener(Integer id) {
        Rol rol = rolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rol", id));
        return toDTO(rol);
    }

    @Transactional
    public RolResponseDTO crear(RolRequestDTO dto) {
        Optional<Rol> existente = rolRepository.findByNombre(dto.getNombre());
        if (existente.isPresent()) {
            throw new ValidationException("Ya existe un rol con ese nombre");
        }

        Rol rol = new Rol();
        rol.setNombre(dto.getNombre());
        rol.setDescripcion(dto.getDescripcion());
        rol = rolRepository.save(rol);

        if (dto.getIdPermisos() != null) {
            for (Integer idPermiso : dto.getIdPermisos()) {
                Permiso permiso = permisoRepository.findById(idPermiso)
                        .orElseThrow(() -> new ResourceNotFoundException("Permiso", idPermiso));
                rolPermisoRepository.save(new RolPermiso(rol, permiso));
            }
        }

        logService.registrarAccion("roles", "crear",
                "Rol #" + rol.getIdRol() + " '" + rol.getNombre() + "' creado con "
                + (dto.getIdPermisos() != null ? dto.getIdPermisos().size() : 0) + " permisos");

        return toDTO(rol);
    }

    @Transactional
    public RolResponseDTO actualizar(Integer id, RolRequestDTO dto) {
        Rol rol = rolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rol", id));

        Optional<Rol> existente = rolRepository.findByNombre(dto.getNombre());
        if (existente.isPresent() && !existente.get().getIdRol().equals(id)) {
            throw new ValidationException("Ya existe un rol con ese nombre");
        }

        rol.setNombre(dto.getNombre());
        rol.setDescripcion(dto.getDescripcion());
        rolRepository.save(rol);

        // Reasignar permisos
        List<RolPermiso> actuales = rolPermisoRepository.findByRolIdRol(id);
        int permisosAntes = actuales.size();
        rolPermisoRepository.deleteAll(actuales);

        if (dto.getIdPermisos() != null) {
            for (Integer idPermiso : dto.getIdPermisos()) {
                Permiso permiso = permisoRepository.findById(idPermiso)
                        .orElseThrow(() -> new ResourceNotFoundException("Permiso", idPermiso));
                rolPermisoRepository.save(new RolPermiso(rol, permiso));
            }
        }

        // Cambiar los permisos de un rol es la operacion mas sensible de toda la
        // aplicacion: redefine lo que puede hacer un grupo entero de usuarios.
        // El detalle fila a fila lo captura trg_auditoria_rol_permiso.
        int permisosDespues = dto.getIdPermisos() != null ? dto.getIdPermisos().size() : 0;
        logService.registrarAccion("roles", "actualizar",
                "Rol #" + id + " '" + rol.getNombre() + "' modificado. Permisos: "
                + permisosAntes + " -> " + permisosDespues);

        return toDTO(rol);
    }

    @Transactional
    public void eliminar(Integer id) {
        Rol rol = rolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rol", id));

        // L14 (D-21): antes esto traia la tabla usuario_rol ENTERA al heap para
        // filtrar en Java lo que la base resuelve con un EXISTS, y arrastraba
        // una variable muerta marcada "// dummy" que no se usaba para nada:
        //     long usuarios = usuarioRolRepository.findByUsuarioIdUsuario(0).size(); // dummy
        if (usuarioRolRepository.existsByRolIdRol(id)) {
            throw new ValidationException("No se puede eliminar: el rol tiene usuarios asignados");
        }

        List<RolPermiso> permisos = rolPermisoRepository.findByRolIdRol(id);
        rolPermisoRepository.deleteAll(permisos);
        rolRepository.delete(rol);

        logService.registrarAccion("roles", "eliminar",
                "Rol #" + id + " '" + rol.getNombre() + "' eliminado junto con sus "
                + permisos.size() + " permisos");
    }

    public RolResponseDTO toDTO(Rol rol) {
        RolResponseDTO dto = new RolResponseDTO();
        dto.setIdRol(rol.getIdRol());
        dto.setNombre(rol.getNombre());
        dto.setDescripcion(rol.getDescripcion());
        dto.setCreatedAt(rol.getCreatedAt());

        List<RolPermiso> rolPermisos = rolPermisoRepository.findByRolIdRol(rol.getIdRol());
        List<PermisoResponseDTO> permisos = rolPermisos.stream()
                .map(rp -> permisoService.toDTO(rp.getPermiso()))
                .collect(Collectors.toList());
        dto.setPermisos(permisos);

        return dto;
    }
}
