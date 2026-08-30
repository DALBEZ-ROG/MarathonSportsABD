package com.marathon.controller;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.auditoria.AuditoriaHistorialDTO;
import com.marathon.dto.auditoria.CambioDatoDTO;
import com.marathon.dto.auditoria.RastroUsuarioDTO;
import com.marathon.dto.auditoria.ResumenHistorialDTO;
import com.marathon.service.AuditoriaCambiosService;
import com.marathon.service.AuditoriaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;
    private final AuditoriaCambiosService cambiosService;

    public AuditoriaController(AuditoriaService auditoriaService,
                               AuditoriaCambiosService cambiosService) {
        this.auditoriaService = auditoriaService;
        this.cambiosService = cambiosService;
    }

    @GetMapping("/inventario")
    @PreAuthorize("hasAuthority('auditoria:ver')")
    public ResponseEntity<PageResponseDTO<AuditoriaHistorialDTO>> listarHistorial(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "idProducto", required = false) Integer idProducto,
            @RequestParam(name = "idBodega", required = false) Integer idBodega,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntity.ok(auditoriaService.listarHistorial(page, size, idProducto, idBodega, desde, hasta));
    }

    @GetMapping("/inventario/resumen")
    @PreAuthorize("hasAuthority('auditoria:ver')")
    public ResponseEntity<ResumenHistorialDTO> resumen(@RequestParam(name = "idProducto") Integer idProducto) {
        return ResponseEntity.ok(auditoriaService.resumenProducto(idProducto));
    }

    // =====================================================================
    // F92 — auditoria_cambios sale a la web
    // =====================================================================
    // La tabla existe desde la F40 y hasta ahora solo se consultaba por psql.
    // Es la unica de las tres bitacoras que dice QUE VALOR TENIA EL DATO ANTES,
    // que es justo la pregunta que no contestaban ni el historial de inventario
    // (solo stock) ni log_accion (solo la accion, no el dato).

    /** El listado campo a campo, con todos los filtros de la pantalla. */
    @GetMapping("/cambios")
    @PreAuthorize("hasAuthority('auditoria:ver')")
    public ResponseEntity<PageResponseDTO<CambioDatoDTO>> listarCambios(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "idUsuario", required = false) Integer idUsuario,
            @RequestParam(name = "tabla", required = false) String tabla,
            @RequestParam(name = "operacion", required = false) String operacion,
            @RequestParam(name = "campo", required = false) String campo,
            @RequestParam(name = "pkValor", required = false) String pkValor,
            @RequestParam(name = "txid", required = false) Long txid,
            @RequestParam(name = "texto", required = false) String texto,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntity.ok(cambiosService.listar(page, size, idUsuario, tabla, operacion,
                campo, pkValor, txid, texto, desde, hasta));
    }

    /**
     * Todo lo que se cambio en una misma transaccion.
     *
     * <p>Un UPDATE que toca tres campos deja tres filas sueltas en la lista.
     * Esto vuelve a juntarlas: es lo que convierte tres apuntes en «esto fue un
     * mismo acto».
     */
    @GetMapping("/cambios/transaccion/{txid}")
    @PreAuthorize("hasAuthority('auditoria:ver')")
    public ResponseEntity<List<CambioDatoDTO>> porTransaccion(@PathVariable long txid) {
        return ResponseEntity.ok(cambiosService.porTransaccion(txid));
    }

    /** Las tablas que hoy tienen disparador de auditoria. Alimenta el desplegable. */
    @GetMapping("/cambios/tablas")
    @PreAuthorize("hasAuthority('auditoria:ver')")
    public ResponseEntity<List<String>> tablasAuditadas() {
        return ResponseEntity.ok(cambiosService.tablasAuditadas());
    }

    /** Los campos que han cambiado alguna vez en una tabla. */
    @GetMapping("/cambios/campos")
    @PreAuthorize("hasAuthority('auditoria:ver')")
    public ResponseEntity<List<String>> camposDe(@RequestParam(name = "tabla") String tabla) {
        return ResponseEntity.ok(cambiosService.camposDe(tabla));
    }

    /**
     * «En que partes del sistema toco algo esta persona, y que toco».
     *
     * <p>Cruza las tres bitacoras y devuelve recuentos por sitio. Desde cada
     * linea, la pantalla salta al detalle ya filtrado.
     */
    @GetMapping("/rastro")
    @PreAuthorize("hasAuthority('auditoria:ver')")
    public ResponseEntity<RastroUsuarioDTO> rastro(
            @RequestParam(name = "idUsuario") Integer idUsuario,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntity.ok(cambiosService.rastroDeUsuario(idUsuario, desde, hasta));
    }
}
