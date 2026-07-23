package com.marathon.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.materiaprima.MateriaPrimaRequestDTO;
import com.marathon.dto.materiaprima.MateriaPrimaResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.MateriaPrima;
import com.marathon.model.UnidadMedida;
import com.marathon.repository.MateriaPrimaRepository;
import com.marathon.repository.UnidadMedidaRepository;

@Service
public class MateriaPrimaService {

    private final MateriaPrimaRepository materiaPrimaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;

    public MateriaPrimaService(MateriaPrimaRepository materiaPrimaRepository,
                               UnidadMedidaRepository unidadMedidaRepository) {
        this.materiaPrimaRepository = materiaPrimaRepository;
        this.unidadMedidaRepository = unidadMedidaRepository;
    }

    public PageResponseDTO<MateriaPrimaResponseDTO> listar(int page, int size, String nombre, String estado) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MateriaPrima> result;

        boolean hasNombre = nombre != null && !nombre.isEmpty();
        boolean hasEstado = estado != null && !estado.isEmpty();

        if (hasNombre && hasEstado) {
            result = materiaPrimaRepository.findByNombreContainingIgnoreCaseAndEstado(nombre, estado, pageable);
        } else if (hasNombre) {
            result = materiaPrimaRepository.findByNombreContainingIgnoreCase(nombre, pageable);
        } else if (hasEstado) {
            result = materiaPrimaRepository.findByEstado(estado, pageable);
        } else {
            result = materiaPrimaRepository.findAll(pageable);
        }

        List<MateriaPrimaResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public MateriaPrimaResponseDTO obtener(Integer id) {
        MateriaPrima mp = materiaPrimaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Materia prima", id));
        return toDTO(mp);
    }

    @Transactional
    public MateriaPrimaResponseDTO crear(MateriaPrimaRequestDTO dto) {
        materiaPrimaRepository.findByNombreIgnoreCase(dto.getNombre()).ifPresent(m -> {
            throw new ValidationException("Ya existe una materia prima con el nombre: " + dto.getNombre());
        });

        MateriaPrima mp = new MateriaPrima();
        mapFromDTO(mp, dto);
        mp.setEstado(dto.getEstado() != null ? dto.getEstado() : "activo");
        mp = materiaPrimaRepository.save(mp);
        return toDTO(mp);
    }

    @Transactional
    public MateriaPrimaResponseDTO actualizar(Integer id, MateriaPrimaRequestDTO dto) {
        MateriaPrima mp = materiaPrimaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Materia prima", id));

        materiaPrimaRepository.findByNombreIgnoreCase(dto.getNombre()).ifPresent(existente -> {
            if (!existente.getIdMateriaPrima().equals(id)) {
                throw new ValidationException("Ya existe una materia prima con el nombre: " + dto.getNombre());
            }
        });

        mapFromDTO(mp, dto);
        if (dto.getEstado() != null) {
            mp.setEstado(dto.getEstado());
        }
        mp = materiaPrimaRepository.save(mp);
        return toDTO(mp);
    }

    @Transactional
    public void eliminar(Integer id) {
        MateriaPrima mp = materiaPrimaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Materia prima", id));
        mp.setEstado("inactivo");
        materiaPrimaRepository.save(mp);
    }

    private void mapFromDTO(MateriaPrima mp, MateriaPrimaRequestDTO dto) {
        mp.setNombre(dto.getNombre());
        mp.setDescripcion(dto.getDescripcion());

        UnidadMedida unidad = unidadMedidaRepository.findById(dto.getIdUnidadMedida())
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", dto.getIdUnidadMedida()));
        mp.setUnidadMedida(unidad);
    }

    private MateriaPrimaResponseDTO toDTO(MateriaPrima mp) {
        MateriaPrimaResponseDTO dto = new MateriaPrimaResponseDTO();
        dto.setIdMateriaPrima(mp.getIdMateriaPrima());
        dto.setNombre(mp.getNombre());
        dto.setDescripcion(mp.getDescripcion());
        dto.setEstado(mp.getEstado());
        dto.setStockActual(mp.getStockActual());
        dto.setStockMinimo(mp.getStockMinimo());
        dto.setCreatedAt(mp.getCreatedAt());
        if (mp.getUnidadMedida() != null) {
            dto.setIdUnidadMedida(mp.getUnidadMedida().getIdUnidadMedida());
            dto.setUnidadMedidaNombre(mp.getUnidadMedida().getNombre());
        }
        return dto;
    }
}
