package com.marathon.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.permiso.PermisoResponseDTO;
import com.marathon.service.PermisoService;

/**
 * Catalogo de permisos.
 *
 * <p><b>Los permisos son DESCRIPTIVOS, no se aplican</b> (defecto D-13, lote
 * L12). Ninguna decision de autorizacion los consulta: {@code SecurityConfig}
 * decide solo por rol ({@code hasAuthority("ROLE_...")}), y el frontend por
 * {@code rolGuard}. Este controlador y la pantalla de roles son un editor del
 * modelo de datos, no un control de acceso.
 *
 * <p><b>Por que no se aplicaron en la L12.</b> Los datos estan incompletos: el
 * rol «Encargado de Produccion» tiene 0 permisos de 49 asignados. Encender la
 * comprobacion dejaria a ese rol sin acceso a nada de un dia para otro. Antes de
 * aplicarlos hay que decidir y cargar que puede hacer cada rol — es una decision
 * de negocio, no un cambio de codigo.
 *
 * <p><b>Por que no se retiraron tampoco.</b> La pantalla de roles usa este
 * endpoint y funciona: permite editar la matriz. Borrarla destruiria el trabajo
 * de definir esa matriz, que es justo el paso previo a poder aplicarla.
 *
 * <p>Lo que si se retiro en la L12 fue lo que <i>aparentaba</i> control de
 * acceso sin serlo: {@code permisoGuard} y {@code AuthService.hasPermiso()} en
 * el frontend, que no los referenciaba ninguna ruta.
 */
@RestController
@RequestMapping("/api/permisos")
public class PermisoController {

    private final PermisoService permisoService;

    public PermisoController(PermisoService permisoService) {
        this.permisoService = permisoService;
    }

    @GetMapping
    public ResponseEntity<List<PermisoResponseDTO>> listar(@RequestParam(required = false) String modulo) {
        if (modulo != null && !modulo.isEmpty()) {
            return ResponseEntity.ok(permisoService.listarPorModulo(modulo));
        }
        return ResponseEntity.ok(permisoService.listarTodos());
    }
}
