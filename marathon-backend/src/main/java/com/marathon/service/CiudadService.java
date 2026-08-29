package com.marathon.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final LogService logService;

    public CiudadService(CiudadRepository ciudadRepository,
                     LogService logService) {
        this.ciudadRepository = ciudadRepository;
        this.logService = logService;
    }

    public PageResponseDTO<CiudadResponseDTO> listar(int page, int size, String nombre, String estado) {
        // F51 (D-41): el ORDEN es obligatorio en una lista paginada.
        // Sin ORDER BY, PostgreSQL devuelve las filas en el orden del monton, y
        // un UPDATE reescribe la fila al final: la que acabas de editar
        // desaparece de su pagina. Ademas, dos paginas consecutivas pueden
        // repetir una fila y esconder otra.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "idCiudad"));
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
        ciudad.setRegion(regionDe(dto));
        ciudad.setEstado(dto.getEstado() != null ? dto.getEstado() : "activo");
        ciudad = ciudadRepository.save(ciudad);

        logService.registrarAccion("ciudades", "crear",
                "Ciudad #" + ciudad.getIdCiudad() + " '" + ciudad.getNombre() + "' creada");

        return toDTO(ciudad);
    }

    public CiudadResponseDTO actualizar(Integer id, CiudadRequestDTO dto) {
        Ciudad ciudad = ciudadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ciudad", id));

        Optional<Ciudad> existente = ciudadRepository.findByNombreIgnoreCase(dto.getNombre());
        if (existente.isPresent() && !existente.get().getIdCiudad().equals(id)) {
            throw new ValidationException("Ya existe una ciudad con ese nombre");
        }

        ciudad.setNombre(dto.getNombre());
        ciudad.setRegion(regionDe(dto));
        if (dto.getEstado() != null) {
            ciudad.setEstado(dto.getEstado());
        }
        ciudad = ciudadRepository.save(ciudad);

        logService.registrarAccion("ciudades", "actualizar",
                "Ciudad #" + ciudad.getIdCiudad() + " '" + ciudad.getNombre() + "' modificada");

        return toDTO(ciudad);
    }

    public void eliminar(Integer id) {
        Ciudad ciudad = ciudadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ciudad", id));
        ciudad.setEstado("inactivo");
        ciudadRepository.save(ciudad);

        logService.registrarAccion("ciudades", "eliminar",
                "Ciudad #" + id + " '" + ciudad.getNombre() + "' dada de baja");
    }

    /**
     * La region que manda el formulario, con el vacio tratado como «no lo se».
     *
     * <p>Un desplegable sin elegir manda cadena vacia, no nulo. Sin esto, esa
     * cadena vacia llegaria al CHECK {@code chk_ciudad_region} y la pantalla
     * recibiria un error de integridad hablando de datos duplicados, que no es
     * lo que pasa. Una ciudad sin clasificar es valida; una con la region en
     * blanco, no.
     */
    private String regionDe(CiudadRequestDTO dto) {
        String region = dto.getRegion();
        return (region == null || region.isBlank()) ? null : region;
    }

    private CiudadResponseDTO toDTO(Ciudad ciudad) {
        return new CiudadResponseDTO(ciudad.getIdCiudad(), ciudad.getNombre(),
                                     ciudad.getRegion(), ciudad.getEstado());
    }
}
