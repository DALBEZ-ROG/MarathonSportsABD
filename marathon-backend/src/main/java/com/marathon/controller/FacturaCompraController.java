package com.marathon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.facturacompra.FacturaCompraRequestDTO;
import com.marathon.dto.facturacompra.FacturaCompraResponseDTO;
import com.marathon.model.Usuario;
import com.marathon.service.FacturaCompraService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/facturas-compra")
public class FacturaCompraController {

    private final FacturaCompraService facturaService;

    public FacturaCompraController(FacturaCompraService facturaService) {
        this.facturaService = facturaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('facturas_compra:ver')")
    public ResponseEntity<PageResponseDTO<FacturaCompraResponseDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer idProveedor) {
        return ResponseEntity.ok(facturaService.listar(page, size, estado, idProveedor));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('facturas_compra:ver')")
    public ResponseEntity<FacturaCompraResponseDTO> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(facturaService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('facturas_compra:registrar')")
    public ResponseEntity<FacturaCompraResponseDTO> crear(@Valid @RequestBody FacturaCompraRequestDTO dto,
                                                          Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(facturaService.crear(dto, usuario.getIdUsuario()));
    }

    /**
     * Documenta de una lo que se recibio, sin pedir nada (F66).
     *
     * <p>Sustituye a la pantalla de formulario: numero, fechas, subtotal e
     * impuesto se deducen de la orden y de sus recepciones. El importe es lo
     * recibido MENOS lo ya documentado, asi que una orden recibida en dos veces
     * no se cobra dos veces.
     */
    @PostMapping("/orden/{idOrdenCompra}/desde-recepcion")
    @PreAuthorize("hasAuthority('facturas_compra:registrar')")
    public ResponseEntity<FacturaCompraResponseDTO> crearDesdeRecepcion(
            @PathVariable Integer idOrdenCompra, Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(facturaService.crearDesdeRecepcion(idOrdenCompra, usuario.getIdUsuario()));
    }

    /** El documento en PDF, listo para imprimir o archivar. */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('facturas_compra:ver')")
    public ResponseEntity<byte[]> pdf(@PathVariable Integer id) {
        FacturaCompraResponseDTO f = facturaService.obtener(id);
        byte[] pdf = facturaService.pdf(id);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        // inline: se abre en una pestana, no se descarga. Es lo que se pidio.
        headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"compra-" + f.getNumeroFacturaProveedor() + ".pdf\"");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('facturas_compra:anular')")
    public ResponseEntity<FacturaCompraResponseDTO> anular(@PathVariable Integer id,
                                                           Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(facturaService.anular(id, usuario.getIdUsuario()));
    }
}
