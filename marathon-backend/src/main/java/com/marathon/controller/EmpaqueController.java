package com.marathon.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.empaque.EmpaqueRequestDTO;
import com.marathon.dto.picking.PickingPedidoDTO;
import com.marathon.dto.pedido.PedidoResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.EmpaqueService;
import com.marathon.service.PickingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/empaque")
public class EmpaqueController {

    private final EmpaqueService empaqueService;
    private final PickingService pickingService;

    public EmpaqueController(EmpaqueService empaqueService, PickingService pickingService) {
        this.empaqueService = empaqueService;
        this.pickingService = pickingService;
    }

    /**
     * Cola de trabajo del empaque: pedidos procesados con el picking COMPLETO
     * (F52, D-42).
     *
     * <p>Antes no existia. La pantalla pedia a {@code /api/picking/pedidos} los
     * 100 primeros pedidos procesados y filtraba en el navegador los que tenian
     * el picking completo. Con 19.059 pedidos en {@code procesado} ordenados del
     * mas antiguo, un pedido recien recogido quedaba el ultimo de la cola y
     * <b>no aparecia nunca</b>: quien lo recogia no podia empacarlo.
     *
     * <p>Lo arma {@code PickingService} porque devuelve el mismo
     * {@code PickingPedidoDTO} —con el detalle de lineas recogidas— que la cola
     * de picking, y duplicar ese mapeo aqui solo daria dos sitios que mantener.
     */
    @GetMapping("/pedidos/listos")
    @PreAuthorize("hasAuthority('empaque:ver')")
    public ResponseEntity<PageResponseDTO<PickingPedidoDTO>> listarListosParaEmpacar(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(pickingService.listarPedidosParaEmpacar(page, size));
    }

    @PostMapping("/pedidos/{idPedido}/confirmar")
    @PreAuthorize("hasAuthority('empaque:confirmar')")
    public ResponseEntity<PedidoResponseDTO> confirmarEmpaque(@PathVariable(name = "idPedido") Integer idPedido,
                                                              @Valid @RequestBody EmpaqueRequestDTO dto) {
        Usuario usuario = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(empaqueService.confirmarEmpaque(idPedido, dto, usuario.getIdUsuario()));
    }

    @GetMapping("/pedidos")
    @PreAuthorize("hasAuthority('empaque:ver')")
    public ResponseEntity<PageResponseDTO<PedidoResponseDTO>> listarDespachados(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "regionDestino", required = false) String regionDestino,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntity.ok(empaqueService.listarDespachados(page, size, regionDestino, desde, hasta));
    }
}
