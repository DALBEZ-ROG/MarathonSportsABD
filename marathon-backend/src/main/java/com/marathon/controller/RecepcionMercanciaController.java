package com.marathon.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.recepcion.RecepcionMercanciaRequestDTO;
import com.marathon.dto.recepcion.RecepcionMercanciaResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.RecepcionMercanciaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recepciones")
public class RecepcionMercanciaController {

    private final RecepcionMercanciaService recepcionService;

    public RecepcionMercanciaController(RecepcionMercanciaService recepcionService) {
        this.recepcionService = recepcionService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('recepciones:registrar')")
    public ResponseEntity<RecepcionMercanciaResponseDTO> crear(@Valid @RequestBody RecepcionMercanciaRequestDTO dto,
                                                               Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recepcionService.crear(dto, usuario.getIdUsuario()));
    }

    @GetMapping("/orden/{idOrdenCompra}")
    @PreAuthorize("hasAuthority('recepciones:ver')")
    public ResponseEntity<List<RecepcionMercanciaResponseDTO>> listarPorOrden(@PathVariable(name = "idOrdenCompra") Integer idOrdenCompra) {
        return ResponseEntity.ok(recepcionService.listarPorOrden(idOrdenCompra));
    }
}
