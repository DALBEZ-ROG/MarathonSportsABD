package com.marathon.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.producto.ProductoRequestDTO;
import com.marathon.dto.producto.ProductoResponseDTO;
import com.marathon.service.ProductoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('productos:ver')")
    public ResponseEntity<PageResponseDTO<ProductoResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idCategoria,
            @RequestParam(required = false) String origen,
            @RequestParam(required = false) Integer idProveedor) {
        return ResponseEntity.ok(productoService.listar(page, size, nombre, estado, idCategoria, origen, idProveedor));
    }

    /**
     * Buscador de producto para los selectores (F93).
     *
     * <p>Busca por PALABRAS y no cuenta el total. Va antes de {@code /{id}} en
     * el fichero por claridad, no por necesidad: Spring da prioridad a la ruta
     * literal sobre la variable, asi que /buscar nunca se confunde con un id.
     */
    @GetMapping("/buscar")
    @PreAuthorize("hasAuthority('productos:ver')")
    public ResponseEntity<List<ProductoResponseDTO>> buscar(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "limite", defaultValue = "20") int limite) {
        return ResponseEntity.ok(productoService.buscarParaSelector(q, limite));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('productos:ver')")
    public ResponseEntity<ProductoResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(productoService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('productos:crear')")
    public ResponseEntity<ProductoResponseDTO> crear(@Valid @RequestBody ProductoRequestDTO dto) {
        return new ResponseEntity<>(productoService.crear(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('productos:editar')")
    public ResponseEntity<ProductoResponseDTO> actualizar(@PathVariable Integer id,
                                                           @Valid @RequestBody ProductoRequestDTO dto) {
        return ResponseEntity.ok(productoService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('productos:eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
