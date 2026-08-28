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
import com.marathon.dto.produccion.CompletarProduccionDTO;
import com.marathon.dto.produccion.OrdenProduccionRequestDTO;
import com.marathon.dto.produccion.OrdenProduccionResponseDTO;
import com.marathon.dto.produccion.VerificacionDisponibilidadDTO;
import com.marathon.model.Usuario;
import com.marathon.service.OrdenProduccionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ordenes-produccion")
public class OrdenProduccionController {

    private final OrdenProduccionService ordenProduccionService;

    public OrdenProduccionController(OrdenProduccionService ordenProduccionService) {
        this.ordenProduccionService = ordenProduccionService;
    }

    @GetMapping("/verificar-disponibilidad")
    @PreAuthorize("hasAuthority('produccion:ver')")
    public ResponseEntity<VerificacionDisponibilidadDTO> verificarDisponibilidad(
            @RequestParam Integer idProducto,
            @RequestParam Integer cantidad) {
        return ResponseEntity.ok(ordenProduccionService.verificarDisponibilidad(idProducto, cantidad));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('produccion:ver')")
    public ResponseEntity<PageResponseDTO<OrdenProduccionResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idProducto,
            @RequestParam(required = false) String busqueda) {
        return ResponseEntity.ok(ordenProduccionService.listar(page, size, estado, idProducto, busqueda));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('produccion:ver')")
    public ResponseEntity<OrdenProduccionResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(ordenProduccionService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('produccion:crear')")
    public ResponseEntity<OrdenProduccionResponseDTO> crear(
            @Valid @RequestBody OrdenProduccionRequestDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return new ResponseEntity<>(
                ordenProduccionService.crear(dto, usuario.getIdUsuario()), HttpStatus.CREATED);
    }

    @PutMapping("/{id}/iniciar")
    @PreAuthorize("hasAuthority('produccion:iniciar')")
    public ResponseEntity<OrdenProduccionResponseDTO> iniciar(
            @PathVariable Integer id, Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(ordenProduccionService.iniciar(id, usuario.getIdUsuario()));
    }

    @PutMapping("/{id}/completar")
    @PreAuthorize("hasAuthority('produccion:completar')")
    public ResponseEntity<OrdenProduccionResponseDTO> completar(
            @PathVariable Integer id,
            @Valid @RequestBody CompletarProduccionDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(ordenProduccionService.completar(id, dto, usuario.getIdUsuario()));
    }

    @PutMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('produccion:cancelar')")
    public ResponseEntity<OrdenProduccionResponseDTO> cancelar(
            @PathVariable Integer id, Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(ordenProduccionService.cancelar(id, usuario.getIdUsuario()));
    }
}
