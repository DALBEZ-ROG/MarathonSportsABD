package com.marathon.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.proveedor.ProveedorRequestDTO;
import com.marathon.dto.proveedor.ProveedorResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.model.Proveedor;
import com.marathon.repository.ProveedorRepository;

@Service
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final LogService logService;

    public ProveedorService(ProveedorRepository proveedorRepository,
                        LogService logService) {
        this.proveedorRepository = proveedorRepository;
        this.logService = logService;
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
        logService.fijarContextoUsuario();
        Proveedor proveedor = new Proveedor();
        mapFromDTO(proveedor, dto);
        proveedor.setEstado(dto.getEstado() != null ? dto.getEstado() : "activo");
        proveedor = proveedorRepository.save(proveedor);

        logService.registrarAccion("proveedores", "crear",
                "Proveedor #" + proveedor.getIdProveedor() + " '" + proveedor.getNombre() + "' creado");

        return toDTO(proveedor);
    }

    public ProveedorResponseDTO actualizar(Integer id, ProveedorRequestDTO dto) {
        logService.fijarContextoUsuario();
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));

        mapFromDTO(proveedor, dto);
        if (dto.getEstado() != null) {
            proveedor.setEstado(dto.getEstado());
        }
        proveedor = proveedorRepository.save(proveedor);

        logService.registrarAccion("proveedores", "actualizar",
                "Proveedor #" + id + " '" + proveedor.getNombre() + "' modificado");

        return toDTO(proveedor);
    }

    public void eliminar(Integer id) {
        logService.fijarContextoUsuario();
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));
        proveedor.setEstado("inactivo");
        proveedorRepository.save(proveedor);

        logService.registrarAccion("proveedores", "eliminar",
                "Proveedor #" + id + " '" + proveedor.getNombre() + "' dado de baja (estado=inactivo)");
    }

    private void mapFromDTO(Proveedor proveedor, ProveedorRequestDTO dto) {
        proveedor.setNombre(dto.getNombre());
        proveedor.setContacto(dto.getRuc());
        proveedor.setDireccion(dto.getDireccion());
        proveedor.setTelefono(dto.getTelefono());
        proveedor.setCorreo(dto.getEmail());
    }

    private ProveedorResponseDTO toDTO(Proveedor proveedor) {
        ProveedorResponseDTO dto = new ProveedorResponseDTO();
        dto.setIdProveedor(proveedor.getIdProveedor());
        dto.setNombre(proveedor.getNombre());
        dto.setRuc(proveedor.getContacto());
        dto.setDireccion(proveedor.getDireccion());
        dto.setTelefono(proveedor.getTelefono());
        dto.setEmail(proveedor.getCorreo());
        dto.setEstado(proveedor.getEstado());
        dto.setCreatedAt(proveedor.getCreatedAt());
        return dto;
    }
}
