package com.marathon.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.bodega.BodegaRequestDTO;
import com.marathon.dto.bodega.BodegaResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.model.Bodega;
import com.marathon.model.Ciudad;
import com.marathon.repository.BodegaRepository;
import com.marathon.repository.CiudadRepository;

@Service
public class BodegaService {

    private final BodegaRepository bodegaRepository;
    private final CiudadRepository ciudadRepository;

    public BodegaService(BodegaRepository bodegaRepository, CiudadRepository ciudadRepository) {
        this.bodegaRepository = bodegaRepository;
        this.ciudadRepository = ciudadRepository;
    }

    public PageResponseDTO<BodegaResponseDTO> listar(int page, int size, String nombre, String estado) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Bodega> result;

        if (nombre != null && !nombre.isEmpty() && estado != null && !estado.isEmpty()) {
            result = bodegaRepository.findByNombreContainingIgnoreCaseAndEstado(nombre, estado, pageable);
        } else if (nombre != null && !nombre.isEmpty()) {
            result = bodegaRepository.findByNombreContainingIgnoreCase(nombre, pageable);
        } else if (estado != null && !estado.isEmpty()) {
            result = bodegaRepository.findByEstado(estado, pageable);
        } else {
            result = bodegaRepository.findAll(pageable);
        }

        List<BodegaResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public List<BodegaResponseDTO> listarActivas() {
        return bodegaRepository.findByEstado("activo").stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public BodegaResponseDTO obtener(Integer id) {
        Bodega bodega = bodegaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", id));
        return toDTO(bodega);
    }

    public BodegaResponseDTO crear(BodegaRequestDTO dto) {
        Bodega bodega = new Bodega();
        mapFromDTO(bodega, dto);
        bodega.setEstado(dto.getEstado() != null ? dto.getEstado() : "activo");
        return toDTO(bodegaRepository.save(bodega));
    }

    public BodegaResponseDTO actualizar(Integer id, BodegaRequestDTO dto) {
        Bodega bodega = bodegaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", id));

        mapFromDTO(bodega, dto);
        if (dto.getEstado() != null) {
            bodega.setEstado(dto.getEstado());
        }
        return toDTO(bodegaRepository.save(bodega));
    }

    public void eliminar(Integer id) {
        Bodega bodega = bodegaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", id));
        bodega.setEstado("inactivo");
        bodegaRepository.save(bodega);
    }

    private void mapFromDTO(Bodega bodega, BodegaRequestDTO dto) {
        bodega.setNombre(dto.getNombre());
        bodega.setDireccion(dto.getDireccion());

        if (dto.getIdCiudad() != null) {
            Ciudad ciudad = ciudadRepository.findById(dto.getIdCiudad())
                    .orElseThrow(() -> new ResourceNotFoundException("Ciudad", dto.getIdCiudad()));
            bodega.setCiudad(ciudad);
        }
    }

    private BodegaResponseDTO toDTO(Bodega bodega) {
        BodegaResponseDTO dto = new BodegaResponseDTO();
        dto.setIdBodega(bodega.getIdBodega());
        dto.setNombre(bodega.getNombre());
        dto.setDireccion(bodega.getDireccion());
        dto.setEstado(bodega.getEstado());
        dto.setCreatedAt(bodega.getCreatedAt());
        if (bodega.getCiudad() != null) {
            dto.setIdCiudad(bodega.getCiudad().getIdCiudad());
            dto.setCiudadNombre(bodega.getCiudad().getNombre());
        }
        return dto;
    }
}
