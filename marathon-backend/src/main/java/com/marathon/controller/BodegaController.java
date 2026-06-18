package com.marathon.controller;

import java.util.List;

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
import com.marathon.dto.bodega.BodegaRequestDTO;
import com.marathon.dto.bodega.BodegaResponseDTO;
import com.marathon.service.BodegaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bodegas")
public class BodegaController {

    private final BodegaService bodegaService;

    public BodegaController(BodegaService bodegaService) {
        this.bodegaService = bodegaService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<BodegaResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(bodegaService.listar(page, size, nombre, estado));
    }

    @GetMapping("/activas")
    public ResponseEntity<List<BodegaResponseDTO>> listarActivas() {
        return ResponseEntity.ok(bodegaService.listarActivas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BodegaResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(bodegaService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<BodegaResponseDTO> crear(@Valid @RequestBody BodegaRequestDTO dto) {
        return new ResponseEntity<>(bodegaService.crear(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BodegaResponseDTO> actualizar(@PathVariable Integer id,
                                                         @Valid @RequestBody BodegaRequestDTO dto) {
        return ResponseEntity.ok(bodegaService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        bodegaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
