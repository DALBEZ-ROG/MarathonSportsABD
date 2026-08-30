package com.marathon.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marathon.dto.respaldo.ConfirmacionRequestDTO;
import com.marathon.dto.respaldo.EstadoRespaldosDTO;
import com.marathon.dto.respaldo.OperacionDTO;
import com.marathon.dto.respaldo.RespaldoDTO;
import com.marathon.model.Respaldo;
import com.marathon.model.Usuario;
import com.marathon.service.RespaldoService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Respaldos, borrado y restauracion (F92).
 *
 * <p>Los cuatro permisos estan separados a proposito, y no es burocracia:
 * {@code respaldos:crear} no destruye nada, {@code respaldos:restaurar} y
 * {@code respaldos:borrar} si. Con un unico permiso «respaldos» no se podria
 * dejar que alguien tome copias sin darle ademas la llave de vaciar la base.
 */
@RestController
@RequestMapping("/api/respaldos")
public class RespaldoController {

    private final RespaldoService respaldoService;
    private final HttpServletRequest request;

    public RespaldoController(RespaldoService respaldoService, HttpServletRequest request) {
        this.respaldoService = respaldoService;
        this.request = request;
    }

    /** Todo lo que la pantalla necesita al abrirse, y cada dos segundos si algo corre. */
    @GetMapping("/estado")
    @PreAuthorize("hasAuthority('respaldos:ver')")
    public ResponseEntity<EstadoRespaldosDTO> estado() {
        return ResponseEntity.ok(respaldoService.estado());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('respaldos:ver')")
    public ResponseEntity<List<RespaldoDTO>> listar() {
        return ResponseEntity.ok(respaldoService.listarRespaldos());
    }

    /** El diario de borrados y restauraciones. */
    @GetMapping("/operaciones")
    @PreAuthorize("hasAuthority('respaldos:ver')")
    public ResponseEntity<List<OperacionDTO>> operaciones() {
        return ResponseEntity.ok(respaldoService.listarOperaciones());
    }

    /**
     * Que se vaciaria exactamente, ANTES de pedir la confirmacion.
     *
     * <p>Sin esto, «borrar los datos» seria una promesa vaga. Con esto, quien
     * pulsa ve la lista de tablas y el numero de filas, y descubre por ejemplo
     * que {@code historial_inventario} se va aunque no haya marcado nada, porque
     * cuelga de {@code inventario} por clave ajena.
     */
    // El nombre del parametro va ESCRITO en la anotacion, no deducido.
    // Spring solo puede deducirlo si la clase se compilo con -parameters, y en
    // este proyecto eso no esta garantizado: el compilador del IDE escribe en
    // target/classes sin ese flag, y entonces Maven da la clase por actualizada
    // y no la vuelve a compilar. El sintoma es un 500 con
    // «Name for argument of type [boolean] not specified», que no se parece en
    // nada a su causa. Escribiendo el nombre, da igual quien compile.
    @GetMapping("/vista-previa-borrado")
    @PreAuthorize("hasAuthority('respaldos:borrar')")
    public ResponseEntity<Map<String, Object>> vistaPreviaBorrado(
            @RequestParam(name = "borrarBitacoras", defaultValue = "false") boolean borrarBitacoras) {
        List<String> tablas = respaldoService.tablasQueSeVaciarian(borrarBitacoras);
        return ResponseEntity.ok(Map.of(
                "tablas", tablas,
                "cuantasTablas", tablas.size(),
                "filasEstimadas", respaldoService.filasEstimadas(tablas)));
    }

    /** El boton de «guardar ahora». Vuelve enseguida; el trabajo va aparte. */
    @PostMapping
    @PreAuthorize("hasAuthority('respaldos:crear')")
    public ResponseEntity<RespaldoDTO> crear(@RequestBody(required = false) ConfirmacionRequestDTO req) {
        Usuario u = usuarioActual();
        String nota = req != null ? req.getNota() : null;
        return ResponseEntity.ok(respaldoService.respaldar(
                Respaldo.ORIGEN_MANUAL, nota,
                u != null ? u.getIdUsuario() : null, nombreDe(u)));
    }

    /** Vaciar la base: el simulacro de «se ha danado el servidor». */
    @PostMapping("/borrar-datos")
    @PreAuthorize("hasAuthority('respaldos:borrar')")
    public ResponseEntity<OperacionDTO> borrarDatos(
            @RequestBody ConfirmacionRequestDTO req,
            @RequestParam(name = "borrarBitacoras", defaultValue = "false") boolean borrarBitacoras) {
        Usuario u = usuarioActual();
        return ResponseEntity.ok(respaldoService.borrarDatos(req, borrarBitacoras,
                u != null ? u.getIdUsuario() : null, nombreDe(u), request.getRemoteAddr()));
    }

    /** Volver a un punto de recuperacion. Vuelve enseguida; el trabajo va aparte. */
    @PostMapping("/restaurar")
    @PreAuthorize("hasAuthority('respaldos:restaurar')")
    public ResponseEntity<OperacionDTO> restaurar(@RequestBody ConfirmacionRequestDTO req) {
        Usuario u = usuarioActual();
        return ResponseEntity.ok(respaldoService.restaurar(req,
                u != null ? u.getIdUsuario() : null, nombreDe(u), request.getRemoteAddr()));
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Usuario u) {
            return u;
        }
        return null;
    }

    /**
     * El nombre se COPIA al diario, no se referencia.
     *
     * <p>La restauracion reemplaza la tabla {@code usuario} entera con la del
     * volcado. Si el diario guardara solo el id, «quien restauro» pasaria a
     * resolverse contra una tabla distinta de aquella en la que esa persona
     * existia. El nombre copiado sigue diciendo la verdad pase lo que pase.
     */
    private String nombreDe(Usuario u) {
        return u != null ? (u.getNombre() + " " + u.getApellido()) : null;
    }
}
