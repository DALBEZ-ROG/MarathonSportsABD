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
import com.marathon.repository.MateriaPrimaRepository;
import com.marathon.repository.ProductoRepository;
import com.marathon.repository.UnidadMedidaRepository;

@Service
public class UnidadMedidaService {

    private final UnidadMedidaRepository unidadMedidaRepository;
    private final LogService logService;

    private final ProductoRepository productoRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;

    public UnidadMedidaService(UnidadMedidaRepository unidadMedidaRepository,
                               ProductoRepository productoRepository,
                               MateriaPrimaRepository materiaPrimaRepository,
                           LogService logService) {
        this.unidadMedidaRepository = unidadMedidaRepository;
        this.productoRepository = productoRepository;
        this.materiaPrimaRepository = materiaPrimaRepository;
        this.logService = logService;
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
        um = unidadMedidaRepository.save(um);

        logService.registrarAccion("unidades-medida", "crear",
                "Unidad de medida #" + um.getIdUnidadMedida() + " '" + um.getNombre() + "' creada");

        return toDTO(um);
    }

    public UnidadMedidaResponseDTO actualizar(Integer id, UnidadMedidaRequestDTO dto) {
        UnidadMedida um = unidadMedidaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", id));

        Optional<UnidadMedida> porNombre = unidadMedidaRepository.findByNombreIgnoreCase(dto.getNombre());
        if (porNombre.isPresent() && !porNombre.get().getIdUnidadMedida().equals(id)) {
            throw new ValidationException("Ya existe una unidad de medida con ese nombre");
        }

        Optional<UnidadMedida> porAbrev = unidadMedidaRepository.findByAbreviaturaIgnoreCase(dto.getAbreviatura());
        if (porAbrev.isPresent() && !porAbrev.get().getIdUnidadMedida().equals(id)) {
            throw new ValidationException("Ya existe una unidad de medida con esa abreviatura");
        }

        um.setNombre(dto.getNombre());
        um.setAbreviatura(dto.getAbreviatura());
        um = unidadMedidaRepository.save(um);

        logService.registrarAccion("unidades-medida", "actualizar",
                "Unidad de medida #" + um.getIdUnidadMedida() + " '" + um.getNombre() + "' modificada");

        return toDTO(um);
    }

    public void eliminar(Integer id) {
        UnidadMedida um = unidadMedidaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", id));

        // L9 (D-20): mismo caso que categoria. Aqui hay DOS tablas que la usan,
        // producto y materia_prima, y las dos con FK ON DELETE RESTRICT.
        if (productoRepository.existsByUnidadMedidaIdUnidadMedida(id)
                || materiaPrimaRepository.existsByUnidadMedidaIdUnidadMedida(id)) {
            throw new ValidationException("No se puede eliminar la unidad '" + um.getNombre()
                    + "': hay productos o materias primas que la usan.");
        }

        unidadMedidaRepository.delete(um);

        logService.registrarAccion("unidades-medida", "eliminar",
                "Unidad de medida #" + id + " '" + um.getNombre() + "' dada de baja");
    }

    private UnidadMedidaResponseDTO toDTO(UnidadMedida um) {
        return new UnidadMedidaResponseDTO(um.getIdUnidadMedida(), um.getNombre(), um.getAbreviatura());
    }
}
