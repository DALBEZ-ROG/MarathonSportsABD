package com.marathon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.devolucion.InspeccionRequestDTO;
import com.marathon.dto.devolucion.ReembolsoRequestDTO;
import com.marathon.dto.devolucion.SolicitudDevolucionRequestDTO;
import com.marathon.dto.devolucion.SolicitudDevolucionResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.SolicitudDevolucionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/devoluciones")
public class SolicitudDevolucionController {

    private final SolicitudDevolucionService service;

    public SolicitudDevolucionController(SolicitudDevolucionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('devoluciones:ver')")
    public ResponseEntity<PageResponseDTO<SolicitudDevolucionResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idPedido,
            @RequestParam(required = false) String busqueda) {
        return ResponseEntity.ok(service.listar(page, size, estado, idPedido, busqueda));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('devoluciones:ver')")
    public ResponseEntity<SolicitudDevolucionResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('devoluciones:crear')")
    public ResponseEntity<SolicitudDevolucionResponseDTO> crear(
            @Valid @RequestBody SolicitudDevolucionRequestDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.crear(dto, usuario.getIdUsuario()));
    }

    @PutMapping("/{id}/iniciar-inspeccion")
    @PreAuthorize("hasAuthority('devoluciones:inspeccionar')")
    public ResponseEntity<SolicitudDevolucionResponseDTO> iniciarInspeccion(
            @PathVariable Integer id, Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(service.iniciarInspeccion(id, usuario.getIdUsuario()));
    }

    @PutMapping("/{id}/inspeccionar")
    @PreAuthorize("hasAuthority('devoluciones:inspeccionar')")
    public ResponseEntity<SolicitudDevolucionResponseDTO> inspeccionar(
            @PathVariable Integer id,
            @Valid @RequestBody InspeccionRequestDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(service.inspeccionar(id, dto, usuario.getIdUsuario()));
    }

    @PostMapping("/{id}/reembolso")
    @PreAuthorize("hasAuthority('devoluciones:reembolsar')")
    public ResponseEntity<SolicitudDevolucionResponseDTO> registrarReembolso(
            @PathVariable Integer id,
            @Valid @RequestBody ReembolsoRequestDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(service.registrarReembolso(id, dto, usuario.getIdUsuario()));
    }
}
