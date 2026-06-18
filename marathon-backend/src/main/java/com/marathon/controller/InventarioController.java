package com.marathon.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.inventario.HistorialResponseDTO;
import com.marathon.dto.inventario.InventarioResponseDTO;
import com.marathon.dto.inventario.MovimientoRequestDTO;
import com.marathon.dto.inventario.MovimientoResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.InventarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    private final InventarioService inventarioService;

    public InventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<InventarioResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer idBodega) {
        return ResponseEntity.ok(inventarioService.listar(page, size, idBodega));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventarioResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(inventarioService.obtener(id));
    }

    @GetMapping("/stock-bajo")
    public ResponseEntity<List<InventarioResponseDTO>> stockBajo() {
        return ResponseEntity.ok(inventarioService.stockBajo());
    }

    @GetMapping("/{idProducto}/{idBodega}/movimientos")
    public ResponseEntity<PageResponseDTO<MovimientoResponseDTO>> listarMovimientos(
            @PathVariable Integer idProducto,
            @PathVariable Integer idBodega,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(inventarioService.listarMovimientos(idProducto, idBodega, page, size));
    }

    @GetMapping("/{idInventario}/historial")
    public ResponseEntity<List<HistorialResponseDTO>> listarHistorial(@PathVariable Integer idInventario) {
        return ResponseEntity.ok(inventarioService.listarHistorial(idInventario));
    }

    @PostMapping("/movimiento")
    public ResponseEntity<MovimientoResponseDTO> registrarMovimiento(@Valid @RequestBody MovimientoRequestDTO dto) {
        Usuario usuario = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return new ResponseEntity<>(inventarioService.registrarMovimiento(dto, usuario.getIdUsuario()), HttpStatus.CREATED);
    }
}
