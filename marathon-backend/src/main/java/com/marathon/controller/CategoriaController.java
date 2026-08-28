package com.marathon.controller;

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
import com.marathon.dto.categoria.CategoriaRequestDTO;
import com.marathon.dto.categoria.CategoriaResponseDTO;
import com.marathon.service.CategoriaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('categorias:ver')")
    public ResponseEntity<PageResponseDTO<CategoriaResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nombre) {
        return ResponseEntity.ok(categoriaService.listar(page, size, nombre));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('categorias:ver')")
    public ResponseEntity<CategoriaResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(categoriaService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('categorias:crear')")
    public ResponseEntity<CategoriaResponseDTO> crear(@Valid @RequestBody CategoriaRequestDTO dto) {
        return new ResponseEntity<>(categoriaService.crear(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('categorias:editar')")
    public ResponseEntity<CategoriaResponseDTO> actualizar(@PathVariable Integer id,
                                                            @Valid @RequestBody CategoriaRequestDTO dto) {
        return ResponseEntity.ok(categoriaService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('categorias:eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        categoriaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
