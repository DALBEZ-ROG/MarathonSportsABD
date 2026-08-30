package com.marathon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import com.marathon.dto.materiaprima.MateriaPrimaRequestDTO;
import com.marathon.dto.materiaprima.MateriaPrimaResponseDTO;
import com.marathon.dto.materiaprima.MovimientoMateriaPrimaRequestDTO;
import com.marathon.dto.materiaprima.MovimientoMateriaPrimaResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.MateriaPrimaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/materia-prima")
public class MateriaPrimaController {

    private final MateriaPrimaService materiaPrimaService;

    public MateriaPrimaController(MateriaPrimaService materiaPrimaService) {
        this.materiaPrimaService = materiaPrimaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('materia_prima:ver')")
    public ResponseEntity<PageResponseDTO<MateriaPrimaResponseDTO>> listar(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "nombre", required = false) String nombre,
            @RequestParam(name = "estado", required = false) String estado) {
        return ResponseEntity.ok(materiaPrimaService.listar(page, size, nombre, estado));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('materia_prima:ver')")
    public ResponseEntity<MateriaPrimaResponseDTO> obtener(@PathVariable(name = "id") Integer id) {
        return ResponseEntity.ok(materiaPrimaService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('materia_prima:crear')")
    public ResponseEntity<MateriaPrimaResponseDTO> crear(@Valid @RequestBody MateriaPrimaRequestDTO dto) {
        return new ResponseEntity<>(materiaPrimaService.crear(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('materia_prima:editar')")
    public ResponseEntity<MateriaPrimaResponseDTO> actualizar(@PathVariable(name = "id") Integer id,
                                                              @Valid @RequestBody MateriaPrimaRequestDTO dto) {
        return ResponseEntity.ok(materiaPrimaService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('materia_prima:eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable(name = "id") Integer id) {
        materiaPrimaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // ---- F26: Kardex de movimientos ----

    @PostMapping("/movimiento")
    @PreAuthorize("hasAuthority('materia_prima:movimiento')")
    public ResponseEntity<MovimientoMateriaPrimaResponseDTO> registrarMovimiento(
            @Valid @RequestBody MovimientoMateriaPrimaRequestDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return new ResponseEntity<>(materiaPrimaService.registrarMovimientoManual(dto, usuario.getIdUsuario()), HttpStatus.CREATED);
    }

    @GetMapping("/{id}/movimientos")
    @PreAuthorize("hasAuthority('materia_prima:ver')")
    public ResponseEntity<PageResponseDTO<MovimientoMateriaPrimaResponseDTO>> listarMovimientos(
            @PathVariable(name = "id") Integer id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(materiaPrimaService.listarMovimientos(page, size, id));
    }

    /**
     * Las materias primas bajo mínimos. Acotada a propósito: es una lista de
     * trabajo, y el total exacto lo da {@code /stock-bajo/conteo}.
     */
    @GetMapping("/stock-bajo")
    @PreAuthorize("hasAuthority('materia_prima:ver')")
    public ResponseEntity<java.util.List<MateriaPrimaResponseDTO>> stockBajo(
            @RequestParam(name = "limite", defaultValue = "200") int limite) {
        return ResponseEntity.ok(materiaPrimaService.listarStockBajo(limite));
    }

    /** Solo cuántas hay. Es lo que necesita un aviso, y cuesta una consulta. */
    @GetMapping("/stock-bajo/conteo")
    @PreAuthorize("hasAuthority('materia_prima:ver')")
    public ResponseEntity<Long> conteoStockBajo() {
        return ResponseEntity.ok(materiaPrimaService.contarStockBajo());
    }

    @PutMapping("/{id}/stock-minimo")
    @PreAuthorize("hasAuthority('materia_prima:editar')")
    public ResponseEntity<MateriaPrimaResponseDTO> actualizarStockMinimo(
            @PathVariable(name = "id") Integer id,
            @RequestBody java.util.Map<String, java.math.BigDecimal> body) {
        return ResponseEntity.ok(materiaPrimaService.actualizarStockMinimo(id, body.get("stockMinimo")));
    }
}
