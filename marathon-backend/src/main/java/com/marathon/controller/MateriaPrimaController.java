package com.marathon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.marathon.dto.materiaprima.MateriaPrimaRequestDTO;
import com.marathon.dto.materiaprima.MateriaPrimaResponseDTO;
import com.marathon.service.MateriaPrimaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/materia-prima")
public class MateriaPrimaController {

    private final MateriaPrimaService materiaPrimaService;

    public MateriaPrimaController(MateriaPrimaService materiaPrimaService) {
        this.materiaPrimaService = materiaPrimaService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<MateriaPrimaResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(materiaPrimaService.listar(page, size, nombre, estado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MateriaPrimaResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(materiaPrimaService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<MateriaPrimaResponseDTO> crear(@Valid @RequestBody MateriaPrimaRequestDTO dto) {
        return new ResponseEntity<>(materiaPrimaService.crear(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MateriaPrimaResponseDTO> actualizar(@PathVariable Integer id,
                                                              @Valid @RequestBody MateriaPrimaRequestDTO dto) {
        return ResponseEntity.ok(materiaPrimaService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        materiaPrimaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
