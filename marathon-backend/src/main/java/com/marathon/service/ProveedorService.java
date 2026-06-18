package com.marathon.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.proveedor.ProveedorRequestDTO;
import com.marathon.dto.proveedor.ProveedorResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Ciudad;
import com.marathon.model.Proveedor;
import com.marathon.repository.CiudadRepository;
import com.marathon.repository.ProveedorRepository;

@Service
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final CiudadRepository ciudadRepository;

    public ProveedorService(ProveedorRepository proveedorRepository, CiudadRepository ciudadRepository) {
        this.proveedorRepository = proveedorRepository;
        this.ciudadRepository = ciudadRepository;
    }

    public PageResponseDTO<ProveedorResponseDTO> listar(int page, int size, String nombre, String estado) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Proveedor> result;

        if (nombre != null && !nombre.isEmpty() && estado != null && !estado.isEmpty()) {
            result = proveedorRepository.findByNombreContainingIgnoreCaseAndEstado(nombre, estado, pageable);
        } else if (nombre != null && !nombre.isEmpty()) {
            result = proveedorRepository.findByNombreContainingIgnoreCase(nombre, pageable);
        } else if (estado != null && !estado.isEmpty()) {
            result = proveedorRepository.findByEstado(estado, pageable);
        } else {
            result = proveedorRepository.findAll(pageable);
        }

        List<ProveedorResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public ProveedorResponseDTO obtener(Integer id) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));
        return toDTO(proveedor);
    }

    public ProveedorResponseDTO crear(ProveedorRequestDTO dto) {
        if (dto.getRuc() != null && !dto.getRuc().isEmpty()) {
            Optional<Proveedor> existente = proveedorRepository.findByRuc(dto.getRuc());
            if (existente.isPresent()) {
                throw new ValidationException("Ya existe un proveedor con el RUC: " + dto.getRuc());
            }
        }

        Proveedor proveedor = new Proveedor();
        mapFromDTO(proveedor, dto);
        proveedor.setEstado(dto.getEstado() != null ? dto.getEstado() : "activo");
        return toDTO(proveedorRepository.save(proveedor));
    }

    public ProveedorResponseDTO actualizar(Integer id, ProveedorRequestDTO dto) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));

        if (dto.getRuc() != null && !dto.getRuc().isEmpty()) {
            Optional<Proveedor> existente = proveedorRepository.findByRuc(dto.getRuc());
            if (existente.isPresent() && !existente.get().getIdProveedor().equals(id)) {
                throw new ValidationException("Ya existe un proveedor con el RUC: " + dto.getRuc());
            }
        }

        mapFromDTO(proveedor, dto);
        if (dto.getEstado() != null) {
            proveedor.setEstado(dto.getEstado());
        }
        return toDTO(proveedorRepository.save(proveedor));
    }

    public void eliminar(Integer id) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));
        proveedor.setEstado("inactivo");
        proveedorRepository.save(proveedor);
    }

    private void mapFromDTO(Proveedor proveedor, ProveedorRequestDTO dto) {
        proveedor.setNombre(dto.getNombre());
        proveedor.setRuc(dto.getRuc());
        proveedor.setDireccion(dto.getDireccion());
        proveedor.setTelefono(dto.getTelefono());
        proveedor.setEmail(dto.getEmail());

        if (dto.getIdCiudad() != null) {
            Ciudad ciudad = ciudadRepository.findById(dto.getIdCiudad())
                    .orElseThrow(() -> new ResourceNotFoundException("Ciudad", dto.getIdCiudad()));
            proveedor.setCiudad(ciudad);
        } else {
            proveedor.setCiudad(null);
        }
    }

    private ProveedorResponseDTO toDTO(Proveedor proveedor) {
        ProveedorResponseDTO dto = new ProveedorResponseDTO();
        dto.setIdProveedor(proveedor.getIdProveedor());
        dto.setNombre(proveedor.getNombre());
        dto.setRuc(proveedor.getRuc());
        dto.setDireccion(proveedor.getDireccion());
        dto.setTelefono(proveedor.getTelefono());
        dto.setEmail(proveedor.getEmail());
        dto.setEstado(proveedor.getEstado());
        dto.setCreatedAt(proveedor.getCreatedAt());
        if (proveedor.getCiudad() != null) {
            dto.setIdCiudad(proveedor.getCiudad().getIdCiudad());
            dto.setCiudadNombre(proveedor.getCiudad().getNombre());
        }
        return dto;
    }
}
