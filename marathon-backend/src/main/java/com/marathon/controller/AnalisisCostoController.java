package com.marathon.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.produccion.AnalisisFabricarVsComprarDTO;
import com.marathon.dto.produccion.CostoProductoFabricadoDTO;
import com.marathon.service.AnalisisCostoService;

@RestController
@RequestMapping("/api/analisis-costos")
public class AnalisisCostoController {

    private final AnalisisCostoService analisisCostoService;

    public AnalisisCostoController(AnalisisCostoService analisisCostoService) {
        this.analisisCostoService = analisisCostoService;
    }

    @GetMapping("/fabricar-vs-comprar/{idProducto}")
    @PreAuthorize("hasAuthority('analisis_costos:ver')")
    public ResponseEntity<AnalisisFabricarVsComprarDTO> fabricarVsComprar(@PathVariable Integer idProducto) {
        return ResponseEntity.ok(analisisCostoService.analizarFabricarVsComprar(idProducto));
    }

    @GetMapping("/productos-fabricados")
    @PreAuthorize("hasAuthority('analisis_costos:ver')")
    public ResponseEntity<PageResponseDTO<CostoProductoFabricadoDTO>> productosFabricados(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(analisisCostoService.listarCostosPorProducto(page, size));
    }
}
