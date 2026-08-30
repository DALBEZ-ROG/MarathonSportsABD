package com.marathon.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.comprobante.ComprobanteResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.ComprobanteService;

@RestController
@RequestMapping("/api/comprobantes")
public class ComprobanteController {

    private final ComprobanteService comprobanteService;

    public ComprobanteController(ComprobanteService comprobanteService) {
        this.comprobanteService = comprobanteService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('comprobantes:ver')")
    public ResponseEntity<PageResponseDTO<ComprobanteResponseDTO>> listar(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "numero", required = false) String numero) {
        return ResponseEntity.ok(comprobanteService.listar(page, size, numero));
    }

    @GetMapping("/pedido/{idPedido}")
    @PreAuthorize("hasAuthority('comprobantes:ver')")
    public ResponseEntity<ComprobanteResponseDTO> obtenerPorPedido(@PathVariable(name = "idPedido") Integer idPedido) {
        return ResponseEntity.ok(comprobanteService.obtenerPorPedido(idPedido));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('comprobantes:ver')")
    public ResponseEntity<ComprobanteResponseDTO> obtener(@PathVariable(name = "id") Integer id) {
        return ResponseEntity.ok(comprobanteService.obtener(id));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('comprobantes:ver')")
    public ResponseEntity<byte[]> descargarPDF(@PathVariable(name = "id") Integer id) {
        ComprobanteResponseDTO comprobante = comprobanteService.obtener(id);
        byte[] pdf = comprobanteService.descargarPDF(id);

        String filename = "comprobante-" + comprobante.getNumeroComprobante() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @PostMapping("/pedido/{idPedido}/generar")
    @PreAuthorize("hasAuthority('comprobantes:emitir')")
    public ResponseEntity<ComprobanteResponseDTO> generar(@PathVariable(name = "idPedido") Integer idPedido) {
        Usuario usuario = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(comprobanteService.generarComprobante(idPedido, usuario.getIdUsuario()));
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('comprobantes:anular')")
    public ResponseEntity<ComprobanteResponseDTO> anular(@PathVariable(name = "id") Integer id) {
        Usuario usuario = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(comprobanteService.anular(id, usuario.getIdUsuario()));
    }
}
