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
import com.marathon.dto.unidadmedida.UnidadMedidaRequestDTO;
import com.marathon.dto.unidadmedida.UnidadMedidaResponseDTO;
import com.marathon.service.UnidadMedidaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/unidades-medida")
public class UnidadMedidaController {

    private final UnidadMedidaService unidadMedidaService;

    public UnidadMedidaController(UnidadMedidaService unidadMedidaService) {
        this.unidadMedidaService = unidadMedidaService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<UnidadMedidaResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nombre) {
        return ResponseEntity.ok(unidadMedidaService.listar(page, size, nombre));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UnidadMedidaResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(unidadMedidaService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<UnidadMedidaResponseDTO> crear(@Valid @RequestBody UnidadMedidaRequestDTO dto) {
        return new ResponseEntity<>(unidadMedidaService.crear(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UnidadMedidaResponseDTO> actualizar(@PathVariable Integer id,
                                                               @Valid @RequestBody UnidadMedidaRequestDTO dto) {
        return ResponseEntity.ok(unidadMedidaService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        unidadMedidaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
