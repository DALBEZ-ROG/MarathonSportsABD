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
import com.marathon.dto.proveedor.ProveedorRequestDTO;
import com.marathon.dto.proveedor.ProveedorResponseDTO;
import com.marathon.service.ProveedorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('proveedores:ver')")
    public ResponseEntity<PageResponseDTO<ProveedorResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(proveedorService.listar(page, size, nombre, estado));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('proveedores:ver')")
    public ResponseEntity<ProveedorResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(proveedorService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('proveedores:crear')")
    public ResponseEntity<ProveedorResponseDTO> crear(@Valid @RequestBody ProveedorRequestDTO dto) {
        return new ResponseEntity<>(proveedorService.crear(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('proveedores:editar')")
    public ResponseEntity<ProveedorResponseDTO> actualizar(@PathVariable Integer id,
                                                            @Valid @RequestBody ProveedorRequestDTO dto) {
        return ResponseEntity.ok(proveedorService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('proveedores:eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        proveedorService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
