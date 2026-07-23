package com.marathon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.ordencompra.CambioEstadoOrdenCompraDTO;
import com.marathon.dto.ordencompra.OrdenCompraRequestDTO;
import com.marathon.dto.ordencompra.OrdenCompraResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.OrdenCompraService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ordenes-compra")
public class OrdenCompraController {

    private final OrdenCompraService ordenCompraService;

    public OrdenCompraController(OrdenCompraService ordenCompraService) {
        this.ordenCompraService = ordenCompraService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<OrdenCompraResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idProveedor) {
        return ResponseEntity.ok(ordenCompraService.listar(page, size, estado, idProveedor));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdenCompraResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(ordenCompraService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<OrdenCompraResponseDTO> crear(@Valid @RequestBody OrdenCompraRequestDTO dto,
                                                        Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ordenCompraService.crear(dto, usuario.getIdUsuario()));
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<OrdenCompraResponseDTO> cambiarEstado(@PathVariable Integer id,
                                                                @Valid @RequestBody CambioEstadoOrdenCompraDTO dto,
                                                                Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(ordenCompraService.cambiarEstado(id, dto, usuario.getIdUsuario()));
    }
}
