package com.marathon.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.unidadmedida.UnidadMedidaRequestDTO;
import com.marathon.dto.unidadmedida.UnidadMedidaResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.UnidadMedida;
import com.marathon.repository.UnidadMedidaRepository;

@Service
public class UnidadMedidaService {

    private final UnidadMedidaRepository unidadMedidaRepository;

    public UnidadMedidaService(UnidadMedidaRepository unidadMedidaRepository) {
        this.unidadMedidaRepository = unidadMedidaRepository;
    }

    public PageResponseDTO<UnidadMedidaResponseDTO> listar(int page, int size, String nombre) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UnidadMedida> result;

        if (nombre != null && !nombre.isEmpty()) {
            result = unidadMedidaRepository.findByNombreContainingIgnoreCase(nombre, pageable);
        } else {
            result = unidadMedidaRepository.findAll(pageable);
        }

        List<UnidadMedidaResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public UnidadMedidaResponseDTO obtener(Integer id) {
        UnidadMedida um = unidadMedidaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", id));
        return toDTO(um);
    }

    public UnidadMedidaResponseDTO crear(UnidadMedidaRequestDTO dto) {
        Optional<UnidadMedida> porNombre = unidadMedidaRepository.findByNombreIgnoreCase(dto.getNombre());
        if (porNombre.isPresent()) {
            throw new ValidationException("Ya existe una unidad de medida con ese nombre");
        }

        Optional<UnidadMedida> porAbrev = unidadMedidaRepository.findByAbreviaturaIgnoreCase(dto.getAbreviatura());
        if (porAbrev.isPresent()) {
            throw new ValidationException("Ya existe una unidad de medida con esa abreviatura");
        }

        UnidadMedida um = new UnidadMedida();
        um.setNombre(dto.getNombre());
        um.setAbreviatura(dto.getAbreviatura());
        return toDTO(unidadMedidaRepository.save(um));
    }

    public UnidadMedidaResponseDTO actualizar(Integer id, UnidadMedidaRequestDTO dto) {
        UnidadMedida um = unidadMedidaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", id));

        Optional<UnidadMedida> porNombre = unidadMedidaRepository.findByNombreIgnoreCase(dto.getNombre());
        if (porNombre.isPresent() && !porNombre.get().getIdUnidad().equals(id)) {
            throw new ValidationException("Ya existe una unidad de medida con ese nombre");
        }

        Optional<UnidadMedida> porAbrev = unidadMedidaRepository.findByAbreviaturaIgnoreCase(dto.getAbreviatura());
        if (porAbrev.isPresent() && !porAbrev.get().getIdUnidad().equals(id)) {
            throw new ValidationException("Ya existe una unidad de medida con esa abreviatura");
        }

        um.setNombre(dto.getNombre());
        um.setAbreviatura(dto.getAbreviatura());
        return toDTO(unidadMedidaRepository.save(um));
    }

    public void eliminar(Integer id) {
        UnidadMedida um = unidadMedidaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", id));
        // Eliminación física
        unidadMedidaRepository.delete(um);
    }

    private UnidadMedidaResponseDTO toDTO(UnidadMedida um) {
        return new UnidadMedidaResponseDTO(um.getIdUnidad(), um.getNombre(), um.getAbreviatura());
    }
}
