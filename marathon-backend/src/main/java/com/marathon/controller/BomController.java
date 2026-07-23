package com.marathon.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.bom.ListaMaterialesItemDTO;
import com.marathon.dto.bom.ListaMaterialesResponseDTO;
import com.marathon.dto.producto.ProductoResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.ListaMaterialesService;

import jakarta.validation.Valid;

/**
 * Lista de Materiales (BOM) de un producto fabricado (F27).
 * El BOM vive dentro del modulo Productos.
 */
@RestController
@RequestMapping("/api/productos/{idProducto}")
public class BomController {

    private final ListaMaterialesService listaMaterialesService;

    public BomController(ListaMaterialesService listaMaterialesService) {
        this.listaMaterialesService = listaMaterialesService;
    }

    // GET /api/productos/{idProducto}/bom
    @GetMapping("/bom")
    public ResponseEntity<List<ListaMaterialesResponseDTO>> obtenerBom(
            @PathVariable Integer idProducto) {
        return ResponseEntity.ok(listaMaterialesService.obtenerBomDeProducto(idProducto));
    }

    // PUT /api/productos/{idProducto}/bom  (reemplazo completo del BOM)
    @PutMapping("/bom")
    public ResponseEntity<List<ListaMaterialesResponseDTO>> definirBom(
            @PathVariable Integer idProducto,
            @Valid @RequestBody ListaMaterialesItemDTO dto,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(
                listaMaterialesService.definirBom(idProducto, dto, usuario.getIdUsuario()));
    }

    // PUT /api/productos/{idProducto}/origen  (cambiar origen comprado <-> fabricado)
    @PutMapping("/origen")
    public ResponseEntity<ProductoResponseDTO> cambiarOrigen(
            @PathVariable Integer idProducto,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        String nuevoOrigen = body.get("origen");
        return ResponseEntity.ok(
                listaMaterialesService.cambiarOrigenProducto(idProducto, nuevoOrigen, usuario.getIdUsuario()));
    }
}
