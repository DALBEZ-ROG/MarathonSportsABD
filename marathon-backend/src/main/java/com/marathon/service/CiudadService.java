package com.marathon.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.ciudad.CiudadRequestDTO;
import com.marathon.dto.ciudad.CiudadResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Ciudad;
import com.marathon.repository.CiudadRepository;

@Service
public class CiudadService {

    private final CiudadRepository ciudadRepository;

    public CiudadService(CiudadRepository ciudadRepository) {
        this.ciudadRepository = ciudadRepository;
    }

    public PageResponseDTO<CiudadResponseDTO> listar(int page, int size, String nombre, String estado) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Ciudad> result;

        if (nombre != null && !nombre.isEmpty() && estado != null && !estado.isEmpty()) {
            result = ciudadRepository.findByNombreContainingIgnoreCaseAndEstado(nombre, estado, pageable);
        } else if (nombre != null && !nombre.isEmpty()) {
            result = ciudadRepository.findByNombreContainingIgnoreCase(nombre, pageable);
        } else if (estado != null && !estado.isEmpty()) {
            result = ciudadRepository.findByEstado(estado, pageable);
        } else {
            result = ciudadRepository.findAll(pageable);
        }

        List<CiudadResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public CiudadResponseDTO obtener(Integer id) {
        Ciudad ciudad = ciudadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ciudad", id));
        return toDTO(ciudad);
    }

    public CiudadResponseDTO crear(CiudadRequestDTO dto) {
        Optional<Ciudad> existente = ciudadRepository.findByNombreIgnoreCase(dto.getNombre());
        if (existente.isPresent()) {
            throw new ValidationException("Ya existe una ciudad con ese nombre");
        }

        Ciudad ciudad = new Ciudad();
        ciudad.setNombre(dto.getNombre());
        ciudad.setEstado(dto.getEstado() != null ? dto.getEstado() : "activo");
        return toDTO(ciudadRepository.save(ciudad));
    }

    public CiudadResponseDTO actualizar(Integer id, CiudadRequestDTO dto) {
        Ciudad ciudad = ciudadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ciudad", id));

        Optional<Ciudad> existente = ciudadRepository.findByNombreIgnoreCase(dto.getNombre());
        if (existente.isPresent() && !existente.get().getIdCiudad().equals(id)) {
            throw new ValidationException("Ya existe una ciudad con ese nombre");
        }

        ciudad.setNombre(dto.getNombre());
        if (dto.getEstado() != null) {
            ciudad.setEstado(dto.getEstado());
        }
        return toDTO(ciudadRepository.save(ciudad));
    }

    public void eliminar(Integer id) {
        Ciudad ciudad = ciudadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ciudad", id));
        ciudad.setEstado("inactivo");
        ciudadRepository.save(ciudad);
    }

    private CiudadResponseDTO toDTO(Ciudad ciudad) {
        return new CiudadResponseDTO(ciudad.getIdCiudad(), ciudad.getNombre(), ciudad.getEstado());
    }
}
