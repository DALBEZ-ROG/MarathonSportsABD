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
    public ResponseEntity<VerificacionDisponibilidadDTO> verificarDisponibilidad(
            @RequestParam Integer idProducto,
            @RequestParam Integer cantidad) {
        return ResponseEntity.ok(ordenProduccionService.verificarDisponibilidad(idProducto, cantidad));
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<OrdenProduccionResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idProducto) {
        return ResponseEntity.ok(ordenProduccionService.listar(page, size, estado, idProducto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdenProduccionResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(ordenProduccionService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<OrdenProduccionResponseDTO> crear(
            @Valid @RequestBody OrdenProduccionRequestDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return new ResponseEntity<>(
                ordenProduccionService.crear(dto, usuario.getIdUsuario()), HttpStatus.CREATED);
    }

    @PutMapping("/{id}/iniciar")
    public ResponseEntity<OrdenProduccionResponseDTO> iniciar(
            @PathVariable Integer id, Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(ordenProduccionService.iniciar(id, usuario.getIdUsuario()));
    }

    @PutMapping("/{id}/completar")
    public ResponseEntity<OrdenProduccionResponseDTO> completar(
            @PathVariable Integer id,
            @Valid @RequestBody CompletarProduccionDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(ordenProduccionService.completar(id, dto, usuario.getIdUsuario()));
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<OrdenProduccionResponseDTO> cancelar(
            @PathVariable Integer id, Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(ordenProduccionService.cancelar(id, usuario.getIdUsuario()));
    }
}
