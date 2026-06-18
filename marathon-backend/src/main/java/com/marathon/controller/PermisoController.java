package com.marathon.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.permiso.PermisoResponseDTO;
import com.marathon.service.PermisoService;

@RestController
@RequestMapping("/api/permisos")
public class PermisoController {

    private final PermisoService permisoService;

    public PermisoController(PermisoService permisoService) {
        this.permisoService = permisoService;
    }

    @GetMapping
    public ResponseEntity<List<PermisoResponseDTO>> listar(@RequestParam(required = false) String modulo) {
        if (modulo != null && !modulo.isEmpty()) {
            return ResponseEntity.ok(permisoService.listarPorModulo(modulo));
        }
        return ResponseEntity.ok(permisoService.listarTodos());
    }
}
