package com.marathon.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.log.LogAccionResponseDTO;
import com.marathon.model.LogAccion;
import com.marathon.model.Usuario;
import com.marathon.repository.LogAccionRepository;
import com.marathon.repository.UsuarioRepository;

@Service
public class LogService {

    private final LogAccionRepository logAccionRepository;
    private final UsuarioRepository usuarioRepository;

    public LogService(LogAccionRepository logAccionRepository, UsuarioRepository usuarioRepository) {
        this.logAccionRepository = logAccionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Registra una acción de auditoría. NUNCA lanza excepción: cualquier fallo
     * al registrar el log se traga para no afectar la operación principal.
     */
    public void registrar(Integer idUsuario, String modulo, String accion, String descripcion, String ipAddress) {
        try {
            LogAccion log = new LogAccion();
            if (idUsuario != null) {
                Usuario usuario = usuarioRepository.findById(idUsuario).orElse(null);
                log.setUsuario(usuario);
            }
            log.setModulo(modulo);
            log.setAccion(accion);
            log.setDescripcion(descripcion);
            log.setIpAddress(ipAddress);
            logAccionRepository.save(log);
        } catch (Exception e) {
            System.err.println("Error al registrar log de auditoría: " + e.getMessage());
        }
    }

    public PageResponseDTO<LogAccionResponseDTO> listar(int page, int size, Integer idUsuario, String modulo,
                                                        LocalDateTime desde, LocalDateTime hasta) {
        Integer idU = idUsuario != null ? idUsuario : 0;
        String mod = (modulo != null && !modulo.isEmpty()) ? modulo : "";
        LocalDateTime d = desde != null ? desde : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime h = hasta != null ? hasta : LocalDateTime.of(2999, 12, 31, 23, 59);

        Pageable pageable = PageRequest.of(page, size);
        Page<LogAccion> result = logAccionRepository.buscar(idU, mod, d, h, pageable);

        List<LogAccionResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public List<String> listarModulos() {
        return logAccionRepository.findDistinctModulos();
    }

    private LogAccionResponseDTO toDTO(LogAccion log) {
        LogAccionResponseDTO dto = new LogAccionResponseDTO();
        dto.setIdLog(log.getIdLog());
        dto.setModulo(log.getModulo());
        dto.setAccion(log.getAccion());
        dto.setDescripcion(log.getDescripcion());
        dto.setIpAddress(log.getIpAddress());
        dto.setFecha(log.getFecha());
        Usuario usuario = log.getUsuario();
        if (usuario != null) {
            dto.setIdUsuario(usuario.getIdUsuario());
            dto.setUsuarioNombre(usuario.getNombre());
            dto.setUsuarioApellido(usuario.getApellido());
        }
        return dto;
    }
}
