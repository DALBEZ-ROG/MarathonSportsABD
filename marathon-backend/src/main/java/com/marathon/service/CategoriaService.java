package com.marathon.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.categoria.CategoriaRequestDTO;
import com.marathon.dto.categoria.CategoriaResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Categoria;
import com.marathon.repository.CategoriaRepository;
import com.marathon.repository.ProductoRepository;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final LogService logService;

    private final ProductoRepository productoRepository;

    public CategoriaService(CategoriaRepository categoriaRepository,
                            ProductoRepository productoRepository,
                        LogService logService) {
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
        this.logService = logService;
    }

    public PageResponseDTO<CategoriaResponseDTO> listar(int page, int size, String nombre) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Categoria> result;

        if (nombre != null && !nombre.isEmpty()) {
            result = categoriaRepository.findByNombreContainingIgnoreCase(nombre, pageable);
        } else {
            result = categoriaRepository.findAll(pageable);
        }

        List<CategoriaResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public CategoriaResponseDTO obtener(Integer id) {
        Categoria cat = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", id));
        return toDTO(cat);
    }

    public CategoriaResponseDTO crear(CategoriaRequestDTO dto) {
        Optional<Categoria> existente = categoriaRepository.findByNombreIgnoreCase(dto.getNombre());
        if (existente.isPresent()) {
            throw new ValidationException("Ya existe una categoría con ese nombre");
        }

        Categoria cat = new Categoria();
        cat.setNombre(dto.getNombre());
        cat.setDescripcion(dto.getDescripcion());
        cat = categoriaRepository.save(cat);

        logService.registrarAccion("categorias", "crear",
                "Categoria #" + cat.getIdCategoria() + " '" + cat.getNombre() + "' creada");

        return toDTO(cat);
    }

    public CategoriaResponseDTO actualizar(Integer id, CategoriaRequestDTO dto) {
        Categoria cat = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", id));

        Optional<Categoria> existente = categoriaRepository.findByNombreIgnoreCase(dto.getNombre());
        if (existente.isPresent() && !existente.get().getIdCategoria().equals(id)) {
            throw new ValidationException("Ya existe una categoría con ese nombre");
        }

        cat.setNombre(dto.getNombre());
        cat.setDescripcion(dto.getDescripcion());
        cat = categoriaRepository.save(cat);

        logService.registrarAccion("categorias", "actualizar",
                "Categoria #" + cat.getIdCategoria() + " '" + cat.getNombre() + "' modificada");

        return toDTO(cat);
    }

    public void eliminar(Integer id) {
        Categoria cat = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", id));

        // L9 (D-20): comprobar el uso ANTES de borrar.
        // categoria no tiene columna estado, asi que el borrado es fisico, y la
        // FK fk_producto_categoria es ON DELETE RESTRICT: sin esta comprobacion
        // el intento moria como un 500 con el texto crudo de PostgreSQL.
        if (productoRepository.existsByCategoriaIdCategoria(id)) {
            throw new ValidationException("No se puede eliminar la categoría '" + cat.getNombre()
                    + "': hay productos que la usan. Reasignalos primero.");
        }

        // Eliminación física (no tiene campo estado)
        categoriaRepository.delete(cat);

        logService.registrarAccion("categorias", "eliminar",
                "Categoria #" + id + " '" + cat.getNombre() + "' dada de baja");
    }

    private CategoriaResponseDTO toDTO(Categoria cat) {
        return new CategoriaResponseDTO(cat.getIdCategoria(), cat.getNombre(), cat.getDescripcion());
    }
}
