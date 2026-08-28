package com.marathon.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CifradoService cifradoService;

    public ProveedorService(ProveedorRepository proveedorRepository,
                        LogService logService,
                        CifradoService cifradoService) {
        this.proveedorRepository = proveedorRepository;
        this.logService = logService;
        this.cifradoService = cifradoService;
    }

    public PageResponseDTO<ProveedorResponseDTO> listar(int page, int size, String nombre, String estado) {
        // F51 (D-41): el ORDEN es obligatorio en una lista paginada.
        // Sin ORDER BY, PostgreSQL devuelve las filas en el orden del monton, y
        // un UPDATE reescribe la fila al final: la que acabas de editar
        // desaparece de su pagina. Ademas, dos paginas consecutivas pueden
        // repetir una fila y esconder otra.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "idProveedor"));
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

    // @Transactional desde la F41: el alta son ahora DOS sentencias (el INSERT
    // de Hibernate y el UPDATE de cifrado) que deben ir juntas. Sin ella, un
    // fallo al cifrar dejaria un proveedor guardado con los datos de contacto
    // vacios. Ademas, el SET LOCAL app.current_user_id de fijarContextoUsuario
    // solo sobrevive dentro de una transaccion.
    @Transactional
    public ProveedorResponseDTO crear(ProveedorRequestDTO dto) {
        logService.fijarContextoUsuario();
        Proveedor proveedor = new Proveedor();
        mapFromDTO(proveedor, dto);
        proveedor.setEstado(dto.getEstado() != null ? dto.getEstado() : "activo");
        // saveAndFlush: el UPDATE de cifrado necesita la fila ya insertada y con
        // id_proveedor asignado.
        proveedor = proveedorRepository.saveAndFlush(proveedor);
        cifradoService.guardarDatosProveedor(proveedor, dto.getRuc(), dto.getEmail(),
                                             dto.getTelefono(), dto.getDireccion());

        logService.registrarAccion("proveedores", "crear",
                "Proveedor #" + proveedor.getIdProveedor() + " '" + proveedor.getNombre() + "' creado");

        return toDTO(proveedor);
    }

    @Transactional
    public ProveedorResponseDTO actualizar(Integer id, ProveedorRequestDTO dto) {
        logService.fijarContextoUsuario();
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));

        mapFromDTO(proveedor, dto);
        if (dto.getEstado() != null) {
            proveedor.setEstado(dto.getEstado());
        }
        proveedor = proveedorRepository.saveAndFlush(proveedor);
        cifradoService.guardarDatosProveedor(proveedor, dto.getRuc(), dto.getEmail(),
                                             dto.getTelefono(), dto.getDireccion());

        logService.registrarAccion("proveedores", "actualizar",
                "Proveedor #" + id + " '" + proveedor.getNombre() + "' modificado");

        return toDTO(proveedor);
    }

    @Transactional
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
        // F41: contacto, direccion, telefono y correo NO se asignan aqui. Son
        // campos @Formula de solo lectura sobre columnas cifradas; los persiste
        // CifradoService.
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
