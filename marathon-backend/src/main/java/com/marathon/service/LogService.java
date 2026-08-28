package com.marathon.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.log.LogAccionResponseDTO;
import com.marathon.model.LogAccion;
import com.marathon.model.Usuario;
import com.marathon.repository.LogAccionRepository;
import com.marathon.repository.UsuarioRepository;

@Service
public class LogService {

    private final LogAccionRepository logAccionRepository;
    private final UsuarioRepository usuarioRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public LogService(LogAccionRepository logAccionRepository, UsuarioRepository usuarioRepository) {
        this.logAccionRepository = logAccionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Id del usuario autenticado, o {@code null} si no hay sesión.
     *
     * <p>F40: los servicios de datos maestros (producto, cliente, proveedor,
     * rol, catálogos) no reciben el id del usuario como parámetro, a diferencia
     * de los de pedidos y compras. En lugar de cambiar la firma de treinta y
     * tantos métodos, se resuelve aquí una sola vez desde el contexto de
     * seguridad, que es donde Spring ya lo tiene.
     */
    public Integer idUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Usuario u) {
            return u.getIdUsuario();
        }
        return null;
    }

    /**
     * Atajo de {@link #registrar} que resuelve solo el usuario y no pide la IP.
     * Es la forma que usan los servicios incorporados a la bitácora en la F40.
     */
    public void registrarAccion(String modulo, String accion, String descripcion) {
        registrar(idUsuarioActual(), modulo, accion, descripcion, null);
    }

    /**
     * Publica el usuario autenticado en la variable de sesión
     * {@code app.current_user_id} para la transacción en curso.
     *
     * <p>Es lo que permite que los triggers de auditoría de la F40 rellenen
     * {@code auditoria_cambios.usuario_app}. Sin esto, la bitácora de la base
     * registra el rol (`usuario_bd`) pero no la persona, y con seis cuentas
     * compartidas por rol eso no responde a «quién».
     *
     * <p>Debe llamarse **antes** de la escritura que dispara el trigger, y
     * dentro de la misma transacción: {@code SET LOCAL} muere en el commit.
     * Es la misma convención que ya usaban {@code InventarioService} y
     * {@code SolicitudDevolucionService} para {@code historial_inventario}.
     */
    public void fijarContextoUsuario() {
        Integer id = idUsuarioActual();
        if (id == null) {
            return;   // sin sesión: el trigger dejará usuario_app en NULL, que es el dato correcto
        }
        try {
            entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + id + "'")
                .executeUpdate();
        } catch (Exception e) {
            System.err.println("No se pudo fijar app.current_user_id: " + e.getMessage());
        }
    }

    /**
     * Registra una acción de auditoría. NUNCA lanza excepción: cualquier fallo
     * al registrar el log se traga para no afectar la operación principal.
     *
     * <p><b>Por qué un INSERT nativo y no {@code logAccionRepository.save()}.</b>
     * F40: la bitácora dejó de funcionar para Operador de Bodega y Operador de
     * Pedidos en cuanto se incorporaron sus servicios. El error era
     * {@code permiso denegado a la tabla log_accion}, con el privilegio
     * {@code INSERT} correctamente otorgado y verificado.
     *
     * <p>La causa: {@code id_log} es {@code GENERATED ALWAYS AS IDENTITY}, así
     * que Hibernate recupera la clave con {@code getGeneratedKeys()}, y el
     * driver de PostgreSQL lo implementa añadiendo un {@code RETURNING} a la
     * sentencia. <b>{@code RETURNING} exige {@code SELECT} sobre la tabla</b>, y
     * esos dos roles tienen {@code INSERT} pero no {@code SELECT} sobre
     * {@code log_accion} — a propósito desde la F34: escriben en la bitácora
     * pero no pueden leerla.
     *
     * <p>Se podría haber otorgado {@code SELECT}, pero eso les dejaría leer la
     * bitácora entera y desharía una decisión deliberada de mínimo privilegio.
     * Como aquí no se necesita el id generado, un {@code INSERT} nativo evita
     * el {@code RETURNING} y respeta el modelo. Es el mismo tipo de hallazgo que
     * el de {@code @DynamicUpdate} en la F37: el ORM emite SQL que el modelo de
     * privilegios no anticipaba.
     */
    public void registrar(Integer idUsuario, String modulo, String accion, String descripcion, String ipAddress) {
        try {
            entityManager.createNativeQuery(
                    "INSERT INTO log_accion (id_usuario, modulo, accion, descripcion, ip_address) "
                    + "VALUES (?, ?, ?, ?, ?)")
                .setParameter(1, idUsuario)
                .setParameter(2, modulo)
                .setParameter(3, accion)
                .setParameter(4, descripcion)
                .setParameter(5, ipAddress)
                .executeUpdate();
        } catch (Exception e) {
            System.err.println("Error al registrar log de auditoría: " + e.getMessage());
        }
    }

    /**
     * Como {@link #registrar}, pero en una transacción <b>aparte</b>.
     *
     * <p>Existe porque {@code registrar} comparte la transacción de quien lo
     * llama, y eso lo hace inútil justo cuando más falta hace: <b>al dejar
     * constancia de algo que se rechaza</b>. El rechazo lanza una excepción, la
     * transacción se deshace, y con ella se borra el apunte que acababa de
     * escribirse. El rastro desaparecía exactamente en el caso que se quería
     * rastrear.
     *
     * <p>Se descubrió en la F60 (D-36): la factura por encima de lo recibido se
     * rechazaba bien, pero {@code log_accion} quedaba vacía. Con
     * {@code REQUIRES_NEW} el apunte se confirma por su cuenta y el rechazo del
     * llamador ya no se lo lleva por delante.
     *
     * <p>Cuesta una segunda conexión del pool mientras dura. Es aceptable
     * porque solo se usa en los caminos de rechazo, que son la excepción.
     */
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void registrarAparte(Integer idUsuario, String modulo, String accion,
                                String descripcion, String ipAddress) {
        registrar(idUsuario, modulo, accion, descripcion, ipAddress);
    }

    public PageResponseDTO<LogAccionResponseDTO> listar(int page, int size, Integer idUsuario, String modulo,
                                                        LocalDateTime desde, LocalDateTime hasta) {
        Integer idU = idUsuario != null ? idUsuario : 0;
        String mod = (modulo != null && !modulo.isEmpty()) ? modulo : "";
        LocalDateTime d = desde != null ? desde : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime h = hasta != null ? hasta : LocalDateTime.of(2999, 12, 31, 23, 59);

        // F51 (D-41): sin Sort aqui A PROPOSITO. La consulta lleva su propio
        // ORDER BY en el JPQL, con desempate por id para que la paginacion sea
        // estable. Anadir un Sort aqui lo pisaria.
        Pageable pageable = PageRequest.of(page, size);
        Page<LogAccion> result = logAccionRepository.buscar(idU, mod, d, h, pageable);

        List<LogAccionResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public List<String> listarModulos() {
        return logAccionRepository.findDistinctModulos();
    }

    private LogAccionResponseDTO toDTO(LogAccion log) {
        LogAccionResponseDTO dto = new LogAccionResponseDTO();
        dto.setIdLog(log.getIdLog());
        dto.setModulo(log.getModulo());
        dto.setAccion(log.getAccion());
        dto.setDescripcion(log.getDescripcion());
        dto.setIpAddress(log.getIpAddress());
        dto.setFecha(log.getFecha());
        Usuario usuario = log.getUsuario();
        if (usuario != null) {
            dto.setIdUsuario(usuario.getIdUsuario());
            dto.setUsuarioNombre(usuario.getNombre());
            dto.setUsuarioApellido(usuario.getApellido());
        }
        return dto;
    }
}
