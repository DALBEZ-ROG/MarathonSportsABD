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
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.rol.RolRequestDTO;
import com.marathon.dto.rol.RolResponseDTO;
import com.marathon.service.RolService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/roles")
public class RolController {

    private final RolService rolService;

    public RolController(RolService rolService) {
        this.rolService = rolService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('roles:ver')")
    public ResponseEntity<List<RolResponseDTO>> listar() {
        return ResponseEntity.ok(rolService.listarTodos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:ver')")
    public ResponseEntity<RolResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(rolService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('roles:crear')")
    public ResponseEntity<RolResponseDTO> crear(@Valid @RequestBody RolRequestDTO dto) {
        return new ResponseEntity<>(rolService.crear(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:editar')")
    public ResponseEntity<RolResponseDTO> actualizar(@PathVariable Integer id,
                                                      @Valid @RequestBody RolRequestDTO dto) {
        return ResponseEntity.ok(rolService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        rolService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
