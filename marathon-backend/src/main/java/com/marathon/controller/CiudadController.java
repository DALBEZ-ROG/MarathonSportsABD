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
import com.marathon.dto.ciudad.CiudadRequestDTO;
import com.marathon.dto.ciudad.CiudadResponseDTO;
import com.marathon.service.CiudadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ciudades")
public class CiudadController {

    private final CiudadService ciudadService;

    public CiudadController(CiudadService ciudadService) {
        this.ciudadService = ciudadService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ciudades:ver')")
    public ResponseEntity<PageResponseDTO<CiudadResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(ciudadService.listar(page, size, nombre, estado));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ciudades:ver')")
    public ResponseEntity<CiudadResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(ciudadService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ciudades:crear')")
    public ResponseEntity<CiudadResponseDTO> crear(@Valid @RequestBody CiudadRequestDTO dto) {
        return new ResponseEntity<>(ciudadService.crear(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ciudades:editar')")
    public ResponseEntity<CiudadResponseDTO> actualizar(@PathVariable Integer id,
                                                         @Valid @RequestBody CiudadRequestDTO dto) {
        return ResponseEntity.ok(ciudadService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ciudades:eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        ciudadService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
