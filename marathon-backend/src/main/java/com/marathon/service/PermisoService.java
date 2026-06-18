package com.marathon.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.marathon.dto.permiso.PermisoResponseDTO;
import com.marathon.model.Permiso;
import com.marathon.repository.PermisoRepository;

@Service
public class PermisoService {

    private final PermisoRepository permisoRepository;

    public PermisoService(PermisoRepository permisoRepository) {
        this.permisoRepository = permisoRepository;
    }

    public List<PermisoResponseDTO> listarTodos() {
        return permisoRepository.findAll(Sort.by("modulo", "accion")).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<PermisoResponseDTO> listarPorModulo(String modulo) {
        return permisoRepository.findAll(Sort.by("modulo", "accion")).stream()
                .filter(p -> p.getModulo().equalsIgnoreCase(modulo))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public PermisoResponseDTO toDTO(Permiso p) {
        return new PermisoResponseDTO(p.getIdPermiso(), p.getModulo(), p.getAccion(), p.getDescripcion());
    }
}
