package com.marathon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.facturacompra.FacturaCompraRequestDTO;
import com.marathon.dto.facturacompra.FacturaCompraResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.FacturaCompraService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/facturas-compra")
public class FacturaCompraController {

    private final FacturaCompraService facturaService;

    public FacturaCompraController(FacturaCompraService facturaService) {
        this.facturaService = facturaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('facturas_compra:ver')")
    public ResponseEntity<PageResponseDTO<FacturaCompraResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idProveedor) {
        return ResponseEntity.ok(facturaService.listar(page, size, estado, idProveedor));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('facturas_compra:ver')")
    public ResponseEntity<FacturaCompraResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(facturaService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('facturas_compra:registrar')")
    public ResponseEntity<FacturaCompraResponseDTO> crear(@Valid @RequestBody FacturaCompraRequestDTO dto,
                                                          Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(facturaService.crear(dto, usuario.getIdUsuario()));
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('facturas_compra:anular')")
    public ResponseEntity<FacturaCompraResponseDTO> anular(@PathVariable Integer id,
                                                           Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(facturaService.anular(id, usuario.getIdUsuario()));
    }
}
