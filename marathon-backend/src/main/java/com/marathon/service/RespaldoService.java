package com.marathon.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.config.ModoMantenimiento;
import com.marathon.config.RegistroDePools;
import com.marathon.dto.respaldo.ConfirmacionRequestDTO;
import com.marathon.dto.respaldo.EstadoRespaldosDTO;
import com.marathon.dto.respaldo.OperacionDTO;
import com.marathon.dto.respaldo.RespaldoDTO;
import com.marathon.dto.respaldo.TareaEnCursoDTO;
import com.marathon.exception.ValidationException;
import com.marathon.model.OperacionControl;
import com.marathon.model.Respaldo;
import com.marathon.repository.OperacionControlRepository;
import com.marathon.repository.RespaldoRepository;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * Respaldar, borrar y restaurar la base desde la web (F92).
 *
 * <h2>Por que un volcado logico y no el respaldo diferencial que ya existe</h2>
 *
 * El sistema ya tenia respaldos: {@code scripts/backup/} hace un completo
 * semanal y un diferencial diario con {@code pg_basebackup}, y esta documentado
 * en ESTRATEGIA_RESPALDO.md. <b>Eso no se toca y sigue siendo la copia de
 * seguridad de verdad.</b> Pero no sirve para lo que pide esta pantalla, y la
 * razon es concreta: un respaldo fisico se restaura <i>parando el servicio de
 * PostgreSQL y reemplazando el directorio de datos</i>. Una aplicacion web no
 * puede hacer eso: se estaria serrando la rama en la que esta sentada, hace
 * falta ser administrador de Windows, y al terminar la propia aplicacion estaria
 * caida.
 *
 * <p>Un volcado logico ({@code pg_dump} / {@code pg_restore}) se restaura con el
 * servidor encendido, sobre la base en caliente. Es mas lento en teoria; medido
 * en esta base de 12 GB, con formato directorio y cuatro trabajos en paralelo,
 * el volcado tarda <b>29 segundos</b> y ocupa 2,4 GB. Eso es lo que hace que un
 * boton en una pantalla sea una idea razonable y no una promesa incumplible.
 *
 * <p>Las dos capas conviven y se reparten el trabajo:
 * <ul>
 *   <li><b>Fisica (pg_basebackup, en Tareas Programadas)</b> — el desastre de
 *       verdad: disco muerto, cluster corrupto. RPO 24 h, RTO 2 h.</li>
 *   <li><b>Logica (esta clase, desde la web)</b> — el punto de retorno a mano:
 *       antes de una carga, antes de una prueba, antes de tocar algo. Y el
 *       simulacro, que es lo que se puede ensenar.</li>
 * </ul>
 *
 * <h2>Una tarea a la vez</h2>
 *
 * Las tres operaciones se serializan con {@link #tarea}. No es prudencia
 * generica: restaurar mientras se respalda produce un respaldo de una base a
 * medio reemplazar, es decir, un punto de recuperacion que no sirve y que
 * <i>parece</i> que sirve. Es peor que no tenerlo.
 */
@Service
public class RespaldoService {

    private static final Logger log = LoggerFactory.getLogger(RespaldoService.class);

    /**
     * Lo que hay que teclear para confirmar. Lo comprueba el servidor.
     *
     * <p>No es una palabra generica tipo «CONFIRMAR»: es el nombre de la base.
     * Quien la teclea tiene que saber cual esta borrando, y eso descarta el
     * caso de estar en la pestana equivocada.
     */
    private static final String PALABRA_BORRADO = "BORRAR mod_venta_inve";
    private static final String PALABRA_RESTAURACION = "RESTAURAR mod_venta_inve";

    /**
     * Las tablas que el borrado NO vacia, y por que cada una.
     *
     * <p>Las cinco primeras son la puerta: sin {@code usuario} y sus roles nadie
     * puede volver a entrar, y quien acaba de borrar la base se quedaria fuera
     * del sistema justo cuando necesita pulsar «Restaurar». Un simulacro del que
     * no se puede volver no es un simulacro.
     *
     * <p>{@code token_revocado} va con ellas: es la lista de sesiones cerradas a
     * la fuerza (F60), y vaciarla revalidaria tokens que se habian anulado a
     * proposito.
     */
    private static final Set<String> SIEMPRE_SE_CONSERVAN = Set.of(
        "usuario", "usuario_rol", "rol", "permiso", "rol_permiso", "token_revocado");

    /**
     * Las bitacoras. Se conservan salvo que se pida expresamente lo contrario.
     *
     * <p>La F40 dejo {@code auditoria_cambios} en append-only <i>incluso para el
     * administrador</i>: ni UPDATE ni DELETE, porque una bitacora que el
     * auditado puede editar no prueba nada (AUDITORIA.md §2). Un boton web que
     * la vaciara por omision desharia esa decision de tapadillo. Asi que vaciar
     * las bitacoras es una casilla aparte, apagada por defecto, y quien la marca
     * sabe lo que hace.
     *
     * <p>{@code historial_inventario} NO esta en esta lista aunque tambien sea
     * una bitacora: tiene clave ajena contra {@code inventario}, asi que se va
     * con el inventario quiera uno o no. Que aparezca en la lista previa de
     * tablas a vaciar es la forma honesta de decirlo.
     */
    private static final Set<String> BITACORAS = Set.of("auditoria_cambios", "log_accion");

    @PersistenceContext
    private EntityManager em;

    private final RespaldoRepository respaldoRepository;
    private final OperacionControlRepository operacionRepository;
    private final LogService logService;
    private final ModoMantenimiento mantenimiento;
    private final RegistroDePools pools;

    @Value("${app.respaldo.pg-bin:C:/Program Files/PostgreSQL/18/bin}")
    private String pgBin;

    @Value("${app.respaldo.directorio:C:/respaldos/marathon/web}")
    private String directorio;

    @Value("${app.respaldo.host:localhost}")
    private String host;

    @Value("${app.respaldo.puerto:5432}")
    private int puerto;

    @Value("${app.respaldo.base:mod_venta_inve}")
    private String base;

    @Value("${app.respaldo.usuario:postgres}")
    private String usuario;

    /**
     * La credencial de respaldo. Vacia por omision y NUNCA en un fichero del
     * repositorio: llega por variable de entorno del proceso, igual que la clave
     * de cifrado de la F41. La pone {@code scripts/cifrado/iniciar_backend.ps1}
     * leyendola del {@code .env}, que esta en .gitignore.
     *
     * <p>Sin ella el modulo se declara no disponible y lo dice; no se cae ni
     * ofrece botones que van a fallar.
     */
    @Value("${app.respaldo.password:}")
    private String password;

    @Value("${app.respaldo.jobs:4}")
    private int trabajos;

    /**
     * Cuantos puntos se conservan en disco.
     *
     * <p>Sin esto el modulo se come el disco y lo hace en silencio: 2,4 GB cada
     * noche son 73 GB al mes, y el dia que se llene el volumen el que se cae es
     * el servidor de base de datos, no la pantalla de respaldos. Los scripts de
     * la capa fisica ya tienen su propia retencion (ESTRATEGIA_RESPALDO.md §5);
     * esta capa necesitaba la suya.
     */
    @Value("${app.respaldo.retencion:7}")
    private int retencion;

    @Value("${app.respaldo.automatico.enabled:true}")
    private boolean automaticoActivo;

    @Value("${app.respaldo.automatico.cron:0 0 2 * * *}")
    private String cron;

    /**
     * Un hilo, y solo uno. Las tres operaciones se excluyen entre si, asi que
     * un pool mas grande solo serviria para tener trabajos esperando.
     */
    private final ExecutorService ejecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "respaldos");
                t.setDaemon(true);
                return t;
            });

    /** La tarea en curso, o null. Es el cerrojo y a la vez lo que ve la pantalla. */
    private final AtomicReference<Tarea> tarea = new AtomicReference<>(null);

    public RespaldoService(RespaldoRepository respaldoRepository,
                           OperacionControlRepository operacionRepository,
                           LogService logService,
                           ModoMantenimiento mantenimiento,
                           RegistroDePools pools) {
        this.respaldoRepository = respaldoRepository;
        this.operacionRepository = operacionRepository;
        this.logService = logService;
        this.mantenimiento = mantenimiento;
        this.pools = pools;
    }

    @PreDestroy
    void cerrar() {
        ejecutor.shutdownNow();
    }

    // =====================================================================
    // Estado
    // =====================================================================

    /** Estado del modulo. Es lo que la pantalla pide al abrirse y mientras trabaja. */
    public EstadoRespaldosDTO estado() {
        EstadoRespaldosDTO dto = new EstadoRespaldosDTO();

        String problema = porQueNoSePuede();
        dto.setDisponible(problema == null);
        dto.setMotivo(problema);

        Tarea t = tarea.get();
        if (t != null) {
            dto.setTarea(t.aDTO());
        }

        List<Respaldo> todos = respaldoRepository.findAllByOrderByFechaInicioDesc();
        dto.setTotalRespaldos(todos.size());

        int disponibles = 0;
        long ocupados = 0;
        for (Respaldo r : todos) {
            if (Respaldo.COMPLETADO.equals(r.getEstado()) && existeEnDisco(r)) {
                disponibles++;
                ocupados += r.getTamanoBytes() != null ? r.getTamanoBytes() : 0;
            }
        }
        dto.setTotalDisponibles(disponibles);
        dto.setBytesOcupados(ocupados);

        respaldoRepository.ultimoCompletado().ifPresent(r -> dto.setUltimoRespaldo(aDTO(r)));

        File carpeta = new File(directorio);
        if (carpeta.exists()) {
            dto.setBytesLibresDisco(carpeta.getUsableSpace());
        }

        dto.setAutomaticoActivo(automaticoActivo);
        dto.setAutomaticoCron(cron);
        dto.setAutomaticoDescripcion(describirCron());
        dto.setProximoAutomatico(proximaEjecucion());
        dto.setMantenimiento(mantenimiento.activo());
        dto.setPalabraBorrado(PALABRA_BORRADO);
        dto.setPalabraRestauracion(PALABRA_RESTAURACION);
        return dto;
    }

    /**
     * Por que no se puede usar el modulo, o null si todo esta en su sitio.
     *
     * <p>Cada rama devuelve la frase que dice <b>que hacer</b>, no solo que algo
     * falta. Un «no disponible» a secas obliga a leer el codigo para saber por
     * donde empezar.
     */
    private String porQueNoSePuede() {
        if (password == null || password.isBlank()) {
            return "Falta la credencial de respaldo. El backend tiene que arrancar con "
                 + "PG_SUPERUSER_PASSWORD en el entorno del proceso: eso lo hace "
                 + "scripts\\cifrado\\iniciar_backend.ps1, que la lee del .env. "
                 + "No se guarda en application.properties a proposito.";
        }
        if (!new File(pgBin, "pg_dump.exe").isFile() && !new File(pgBin, "pg_dump").isFile()) {
            return "No se encuentra pg_dump en " + pgBin + ". Ajustar app.respaldo.pg-bin.";
        }
        if (!new File(pgBin, "pg_restore.exe").isFile() && !new File(pgBin, "pg_restore").isFile()) {
            return "No se encuentra pg_restore en " + pgBin + ". Ajustar app.respaldo.pg-bin.";
        }
        File carpeta = new File(directorio);
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            return "No se pudo crear la carpeta de respaldos " + directorio + ".";
        }
        if (!carpeta.canWrite()) {
            return "La carpeta de respaldos " + directorio + " no admite escritura.";
        }
        return null;
    }

    private String describirCron() {
        if (!automaticoActivo) {
            return "Desactivado (app.respaldo.automatico.enabled=false)";
        }
        // Solo se traduce el caso diario, que es el configurado. Para cualquier
        // otro se ensena el cron crudo: inventar una traduccion aproximada de
        // una expresion que no se entiende es peor que ensenar la expresion.
        String[] p = cron.trim().split("\\s+");
        if (p.length == 6 && "*".equals(p[3]) && "*".equals(p[4]) && "*".equals(p[5])) {
            return String.format("Todos los dias a las %s:%s",
                    p[2], p[1].length() == 1 ? "0" + p[1] : p[1]);
        }
        return cron;
    }

    private LocalDateTime proximaEjecucion() {
        if (!automaticoActivo) {
            return null;
        }
        try {
            return CronExpression.parse(cron).next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Cron de respaldo automatico invalido ({}): {}", cron, e.getMessage());
            return null;
        }
    }

    // =====================================================================
    // Listados
    // =====================================================================

    public List<RespaldoDTO> listarRespaldos() {
        return respaldoRepository.findAllByOrderByFechaInicioDesc().stream()
                .map(this::aDTO)
                .toList();
    }

    public List<OperacionDTO> listarOperaciones() {
        List<OperacionControl> operaciones = operacionRepository.findAllByOrderByFechaInicioDesc();
        List<OperacionDTO> lista = new ArrayList<>();
        for (OperacionControl o : operaciones) {
            OperacionDTO dto = new OperacionDTO();
            dto.setIdOperacion(o.getIdOperacion());
            dto.setTipo(o.getTipo());
            dto.setIdRespaldo(o.getIdRespaldo());
            dto.setEstado(o.getEstado());
            dto.setFechaInicio(o.getFechaInicio());
            dto.setFechaFin(o.getFechaFin());
            dto.setDuracionMs(o.getDuracionMs());
            dto.setIdUsuario(o.getIdUsuario());
            dto.setUsuarioNombre(o.getUsuarioNombre());
            dto.setIp(o.getIp());
            dto.setFilasAfectadas(o.getFilasAfectadas());
            dto.setDetalle(o.getDetalle());
            if (o.getIdRespaldo() != null) {
                respaldoRepository.findById(o.getIdRespaldo())
                        .ifPresent(r -> dto.setRespaldoNombre(r.getNombre()));
            }
            lista.add(dto);
        }
        return lista;
    }

    /**
     * Las tablas que se vaciarian, en el orden en que las devuelve la base.
     *
     * <p>La pantalla la ensena ANTES de pedir la confirmacion. No es un adorno:
     * es la unica forma de que quien pulsa sepa que {@code historial_inventario}
     * se va aunque no haya marcado «borrar bitacoras» — se va porque cuelga de
     * {@code inventario} por clave ajena, y eso no es evidente desde fuera.
     */
    public List<String> tablasQueSeVaciarian(boolean borrarBitacoras) {
        Set<String> conservadas = new LinkedHashSet<>(SIEMPRE_SE_CONSERVAN);
        if (!borrarBitacoras) {
            conservadas.addAll(BITACORAS);
        }

        // El cierre transitivo de las claves ajenas. Una tabla que apunta a otra
        // que se vacia TIENE que vaciarse tambien, o el TRUNCATE se niega. Se
        // calcula aqui, con el catalogo, en lugar de escribir la lista a mano:
        // una lista a mano se queda vieja en cuanto alguien anade una tabla, y
        // se queda vieja en silencio.
        Query q = em.createNativeQuery(
            "WITH RECURSIVE objetivo AS ( "
          + "    SELECT c.oid "
          + "    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
          + "    WHERE n.nspname = 'public' AND c.relkind = 'r' "
          + "      AND NOT (c.relname = ANY (?1)) "
          + "  UNION "
          + "    SELECT con.conrelid "
          + "    FROM pg_constraint con JOIN objetivo o ON con.confrelid = o.oid "
          + "    WHERE con.contype = 'f' "
          + ") "
          // cast(... as text), no `::text`: Hibernate confunde el `:text` con un
          // parametro con nombre y la consulta ni llega a la base.
          + "SELECT cast(c.relname AS text) FROM objetivo o "
          + "JOIN pg_class c ON c.oid = o.oid ORDER BY 1");
        q.setParameter(1, conservadas.toArray(new String[0]));

        List<String> tablas = new ArrayList<>();
        for (Object fila : q.getResultList()) {
            tablas.add((String) fila);
        }
        return tablas;
    }

    /**
     * Filas estimadas que se perderian.
     *
     * <p>Del catalogo ({@code n_live_tup}), no de un {@code count(*)}. Contar de
     * verdad serian 50 millones de filas barridas para pintar un numero en un
     * dialogo de aviso, y el numero exacto no cambia la decision de nadie.
     */
    public long filasEstimadas(List<String> tablas) {
        if (tablas.isEmpty()) {
            return 0;
        }
        Query q = em.createNativeQuery(
            "SELECT coalesce(sum(n_live_tup), 0) FROM pg_stat_user_tables "
          + "WHERE schemaname = 'public' AND relname = ANY (?1)");
        q.setParameter(1, tablas.toArray(new String[0]));
        return ((Number) q.getSingleResult()).longValue();
    }

    // =====================================================================
    // Respaldar
    // =====================================================================

    /**
     * Toma un respaldo. Devuelve enseguida: el trabajo va en otro hilo y la
     * pantalla lo sigue por {@code /api/respaldos/estado}.
     */
    public RespaldoDTO respaldar(String origen, String nota, Integer idUsuario, String nombreUsuario) {
        exigirDisponible();
        String sello = LocalDateTime.now().toString()
                .replace("-", "").replace(":", "").replace("T", "_");
        sello = sello.substring(0, Math.min(sello.length(), 15));
        String nombre = ("AUTOMATICO".equals(origen) ? "auto_" : "manual_") + sello;

        Respaldo r = new Respaldo();
        r.setNombre(nombre);
        r.setRuta(Paths.get(directorio, nombre).toString());
        r.setOrigen(origen);
        r.setEstado(Respaldo.EN_CURSO);
        r.setFechaInicio(LocalDateTime.now());
        r.setIdUsuario(idUsuario);
        r.setUsuarioNombre(nombreUsuario);
        r.setNota(nota != null && !nota.isBlank() ? nota.trim() : null);
        Respaldo guardado = guardar(r);

        long esperados = respaldoRepository.ultimoCompletado()
                .map(u -> u.getTamanoBytes() != null ? u.getTamanoBytes() : 0L)
                .orElse(0L);

        Tarea t = new Tarea("RESPALDO", "Volcando la base a disco", esperados,
                            Paths.get(guardado.getRuta()));
        tomarElTurno(t);

        ejecutor.submit(() -> {
            try {
                ejecutarRespaldo(guardado, t);
            } finally {
                t.detenerVigilancia();
                tarea.set(null);
            }
        });

        return aDTO(guardado);
    }

    private void ejecutarRespaldo(Respaldo r, Tarea t) {
        long inicio = System.currentTimeMillis();
        try {
            t.vigilarCarpeta();
            Resultado res = ejecutar(
                    "pg_dump",
                    "--host=" + host, "--port=" + puerto, "--username=" + usuario,
                    "--dbname=" + base,
                    "--format=directory",
                    "--jobs=" + trabajos,
                    "--compress=1",
                    // El diario de respaldos NO entra en el volcado. Si entrara,
                    // restaurar devolveria el diario al estado que tenia cuando
                    // se tomo el respaldo y se perderia la fila que dice quien
                    // acaba de restaurar. Ver fase92_control_respaldos.sql.
                    "--exclude-schema=control",
                    "--file=" + r.getRuta());

            if (res.codigo != 0) {
                throw new IOException("pg_dump devolvio " + res.codigo + ": " + res.salida);
            }

            r.setEstado(Respaldo.COMPLETADO);
            r.setTamanoBytes(tamanoDe(Paths.get(r.getRuta())));
            r.setFilas(filasVivas());
            r.setMensaje(res.salida.isBlank() ? null : recortar(res.salida));
            log.info("Respaldo '{}' completado en {} ms ({} bytes)",
                     r.getNombre(), System.currentTimeMillis() - inicio, r.getTamanoBytes());
            aplicarRetencion(r.getIdRespaldo());
        } catch (Exception e) {
            r.setEstado(Respaldo.FALLIDO);
            r.setMensaje(recortar(e.getMessage()));
            log.error("Respaldo '{}' fallido: {}", r.getNombre(), e.getMessage());
        } finally {
            r.setFechaFin(LocalDateTime.now());
            r.setDuracionMs(System.currentTimeMillis() - inicio);
            guardar(r);
            logService.registrarAparte(r.getIdUsuario(), "respaldos",
                    Respaldo.COMPLETADO.equals(r.getEstado()) ? "respaldo_crear" : "respaldo_fallido",
                    "Respaldo " + r.getNombre() + " (" + r.getOrigen().toLowerCase() + "): "
                    + r.getEstado().toLowerCase(), null);
        }
    }

    /**
     * Deja en disco solo los {@code retencion} puntos mas recientes.
     *
     * <p><b>La fila del diario NO se borra</b>, solo la carpeta. Son dos cosas
     * distintas: la fila dice que el respaldo <i>se hizo</i> —y eso es historia,
     * no se reescribe—, y la carpeta dice si <i>todavia se puede usar</i>. Al
     * quedarse sin carpeta el punto deja de ofrecer el boton de restaurar, y el
     * mensaje explica que fue la retencion y no que alguien la borro a mano.
     *
     * <p>El respaldo recien hecho queda fuera del recuento pase lo que pase: la
     * retencion no puede llevarse por delante lo que se acaba de pedir.
     *
     * <p>Los errores se registran y no se propagan: quedarse sin espacio es un
     * problema, pero fallar al limpiar no puede convertir un respaldo que salio
     * bien en uno que consta como fallido.
     */
    private void aplicarRetencion(Long idRecienHecho) {
        if (retencion <= 0) {
            return;   // 0 o negativo = no purgar nunca. Es una opcion legitima.
        }
        try {
            List<Respaldo> vivos = respaldoRepository
                    .findByEstadoOrderByFechaInicioDesc(Respaldo.COMPLETADO).stream()
                    .filter(this::existeEnDisco)
                    .toList();

            int conservados = 0;
            for (Respaldo r : vivos) {
                if (r.getIdRespaldo().equals(idRecienHecho) || conservados < retencion) {
                    conservados++;
                    continue;
                }
                if (borrarCarpeta(Paths.get(r.getRuta()))) {
                    r.setMensaje("Carpeta eliminada por la politica de retencion: se conserva"
                               + (retencion == 1 ? " solo el punto mas reciente"
                                                 : "n los " + retencion + " puntos mas recientes")
                               + ". El apunte se queda; lo que ya no esta es el volcado.");
                    guardar(r);
                    log.info("Retencion: eliminada la carpeta del respaldo '{}'.", r.getNombre());
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo aplicar la retencion de respaldos: {}", e.getMessage());
        }
    }

    private boolean borrarCarpeta(Path carpeta) {
        try (Stream<Path> contenido = Files.walk(carpeta)) {
            // De dentro hacia fuera: un directorio no se borra si aun tiene algo.
            contenido.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    log.warn("No se pudo borrar {}: {}", p, e.getMessage());
                }
            });
            return !Files.exists(carpeta);
        } catch (IOException e) {
            log.warn("No se pudo recorrer {} para borrarla: {}", carpeta, e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // Borrar
    // =====================================================================

    /**
     * Vacia las tablas de negocio: el simulacro de «se ha danado el servidor».
     *
     * <p>Corre en el hilo de la peticion y no en el ejecutor, porque un TRUNCATE
     * de 46 tablas es cuestion de segundos y devolver el resultado de golpe es
     * mas claro que mandar a la pantalla a preguntar por el.
     */
    public OperacionDTO borrarDatos(ConfirmacionRequestDTO req, boolean borrarBitacoras,
                                    Integer idUsuario, String nombreUsuario, String ip) {
        exigirDisponible();
        exigirConfirmacion(req.getConfirmacion(), PALABRA_BORRADO);
        exigirTurnoLibre();

        // El respaldo previo NO es opcional por comodidad: es lo que convierte
        // «borrar la base» en «simular un desastre». Se puede desmarcar, pero
        // hay que desmarcarlo a conciencia.
        Long idRespaldoPrevio = null;
        if (req.isRespaldarAntes()) {
            RespaldoDTO previo = respaldar(Respaldo.ORIGEN_MANUAL,
                    "Automatico: tomado justo antes de un borrado total", idUsuario, nombreUsuario);
            esperarAQueTermine();
            Respaldo comprobado = respaldoRepository.findById(previo.getIdRespaldo()).orElseThrow();
            if (!Respaldo.COMPLETADO.equals(comprobado.getEstado())) {
                throw new ValidationException(
                    "El respaldo previo fallo, asi que no se borra nada: " + comprobado.getMensaje()
                  + ". Revisa el problema, o desmarca «respaldar antes» si de verdad quieres "
                  + "borrar sin red.");
            }
            idRespaldoPrevio = comprobado.getIdRespaldo();
        }

        List<String> tablas = tablasQueSeVaciarian(borrarBitacoras);
        long filas = filasEstimadas(tablas);

        OperacionControl op = new OperacionControl();
        op.setTipo(OperacionControl.BORRADO_TOTAL);
        op.setEstado(OperacionControl.EN_CURSO);
        op.setFechaInicio(LocalDateTime.now());
        op.setIdUsuario(idUsuario);
        op.setUsuarioNombre(nombreUsuario);
        op.setIp(ip);
        op.setIdRespaldo(idRespaldoPrevio);
        OperacionControl guardada = guardar(op);

        long inicio = System.currentTimeMillis();
        Tarea t = new Tarea("BORRADO", "Vaciando " + tablas.size() + " tablas", 0, null);
        tomarElTurno(t);
        try {
            // Como superusuario y por conexion propia. El pool de la aplicacion
            // se conecta como usr_admin_marathon, que NO tiene TRUNCATE sobre
            // auditoria_cambios —a proposito, F40— y ademas es la conexion que
            // esta atendiendo esta misma peticion.
            try (Connection cn = conexionDeServicio(); Statement st = cn.createStatement()) {
                st.execute("TRUNCATE TABLE " + String.join(", ", tablas) + " RESTART IDENTITY");
            }
            guardada.setEstado(OperacionControl.COMPLETADO);
            guardada.setFilasAfectadas(filas);
            guardada.setDetalle("Vaciadas " + tablas.size() + " tablas ("
                    + (borrarBitacoras ? "incluidas las bitacoras" : "bitacoras conservadas")
                    + "). Conservadas: " + String.join(", ", SIEMPRE_SE_CONSERVAN)
                    + (borrarBitacoras ? "" : ", " + String.join(", ", BITACORAS)) + ".");
            log.warn("BORRADO TOTAL ejecutado por '{}' desde {}: {} tablas, ~{} filas",
                     nombreUsuario, ip, tablas.size(), filas);
        } catch (Exception e) {
            guardada.setEstado(OperacionControl.FALLIDO);
            guardada.setDetalle(recortar(e.getMessage()));
            log.error("Borrado total fallido: {}", e.getMessage());
        } finally {
            guardada.setFechaFin(LocalDateTime.now());
            guardada.setDuracionMs(System.currentTimeMillis() - inicio);
            guardar(guardada);
            tarea.set(null);
            // Los planes preparados del servidor apuntan a las tablas de antes.
            // Un TRUNCATE no cambia la estructura, pero si deja estadisticas y
            // secuencias distintas; reciclar es barato y evita sorpresas.
            pools.reciclarTodo();
        }

        if (OperacionControl.FALLIDO.equals(guardada.getEstado())) {
            throw new ValidationException("No se pudo vaciar la base: " + guardada.getDetalle());
        }
        return listarOperaciones().stream()
                .filter(o -> o.getIdOperacion().equals(guardada.getIdOperacion()))
                .findFirst().orElseThrow();
    }

    // =====================================================================
    // Restaurar
    // =====================================================================

    /** Devuelve enseguida; la restauracion va en el hilo de respaldos. */
    public OperacionDTO restaurar(ConfirmacionRequestDTO req, Integer idUsuario,
                                  String nombreUsuario, String ip) {
        exigirDisponible();
        exigirConfirmacion(req.getConfirmacion(), PALABRA_RESTAURACION);

        if (req.getIdRespaldo() == null) {
            throw new ValidationException("Hay que decir desde que respaldo se restaura.");
        }
        Respaldo r = respaldoRepository.findById(req.getIdRespaldo())
                .orElseThrow(() -> new ValidationException("Ese respaldo no existe."));
        if (!Respaldo.COMPLETADO.equals(r.getEstado())) {
            throw new ValidationException(
                "El respaldo '" + r.getNombre() + "' no termino bien (" + r.getEstado()
              + "), asi que no se puede restaurar desde el.");
        }
        if (!existeEnDisco(r)) {
            throw new ValidationException(
                "El respaldo '" + r.getNombre() + "' consta en el diario pero su carpeta ya no "
              + "esta en " + r.getRuta() + ". Alguien la ha movido o borrado.");
        }

        OperacionControl op = new OperacionControl();
        op.setTipo(OperacionControl.RESTAURACION);
        op.setEstado(OperacionControl.EN_CURSO);
        op.setFechaInicio(LocalDateTime.now());
        op.setIdUsuario(idUsuario);
        op.setUsuarioNombre(nombreUsuario);
        op.setIp(ip);
        op.setIdRespaldo(r.getIdRespaldo());
        OperacionControl guardada = guardar(op);

        Tarea t = new Tarea("RESTAURACION", "Restaurando desde " + r.getNombre(), 0, null);
        tomarElTurno(t);

        ejecutor.submit(() -> {
            try {
                ejecutarRestauracion(r, guardada, t);
            } finally {
                t.detenerVigilancia();
                tarea.set(null);
            }
        });

        OperacionDTO dto = new OperacionDTO();
        dto.setIdOperacion(guardada.getIdOperacion());
        dto.setTipo(guardada.getTipo());
        dto.setEstado(guardada.getEstado());
        dto.setIdRespaldo(r.getIdRespaldo());
        dto.setRespaldoNombre(r.getNombre());
        dto.setFechaInicio(guardada.getFechaInicio());
        // Quien y desde donde. Estaban en la fila guardada pero no se copiaban
        // aqui, y la respuesta inmediata salia con el autor en blanco: parecia
        // que la restauracion no la habia pedido nadie.
        dto.setIdUsuario(guardada.getIdUsuario());
        dto.setUsuarioNombre(guardada.getUsuarioNombre());
        dto.setIp(guardada.getIp());
        return dto;
    }

    private void ejecutarRestauracion(Respaldo r, OperacionControl op, Tarea t) {
        long inicio = System.currentTimeMillis();

        // La foto de los contadores se toma AHORA, con la base todavia entera.
        // Durante la restauracion no se puede consultar nada —ni siquiera la
        // tabla usuario, que es la que autentica— asi que el sondeo de la
        // pantalla se contesta con esta foto mas el progreso vivo de la tarea.
        EstadoRespaldosDTO foto = estado();
        mantenimiento.activar(
            "Se esta restaurando la base de datos desde el respaldo '" + r.getNombre()
          + "'. El sistema vuelve solo en cuanto termine; no hace falta recargar nada.",
            () -> {
                foto.setMantenimiento(true);
                foto.setTarea(t.aDTO());
                return foto;
            });
        try {
            // Cuantos objetos trae el volcado. Es lo que convierte el avance de
            // pg_restore en un porcentaje de verdad en vez de un reloj a ciegas,
            // y en una operacion que deja el sistema parado varios minutos eso
            // es la diferencia entre esperar y no saber si se ha colgado.
            t.fijarObjetivo(contarObjetos(r.getRuta()));

            t.cambiarFase("Reemplazando el contenido de la base");
            Resultado res = ejecutar(
                    "pg_restore",
                    t::apuntarLinea,
                    "--host=" + host, "--port=" + puerto, "--username=" + usuario,
                    "--dbname=" + base,
                    "--format=directory",
                    "--jobs=" + trabajos,
                    // --clean borra cada objeto antes de recrearlo; --if-exists
                    // evita que se queje de los que no estan. Sin los dos, el
                    // restore chocaria con todo lo que ya existe.
                    "--clean", "--if-exists",
                    // --verbose no es para depurar: es la unica fuente de
                    // informacion sobre el avance. pg_restore no ofrece ninguna
                    // otra, y sin ella la pantalla solo puede contar segundos.
                    "--verbose",
                    // Sin --exit-on-error a proposito: se quiere que termine el
                    // trabajo y luego rinda cuentas de lo que no pudo, no que
                    // deje la base a medias al primer tropiezo.
                    "--no-password",
                    r.getRuta());

            String salida = res.salida == null ? "" : res.salida;
            // pg_restore devuelve 0 aunque haya ignorado errores; el recuento
            // solo aparece en la salida. Mirar solo el codigo de salida daria
            // por buena una restauracion incompleta.
            boolean huboErrores = salida.contains("errors ignored on restore")
                               || salida.contains("errores ignorados");

            if (res.codigo != 0) {
                throw new IOException("pg_restore devolvio " + res.codigo + ": " + salida);
            }

            t.cambiarFase("Renovando las conexiones");
            pools.reciclarTodo();

            op.setEstado(OperacionControl.COMPLETADO);
            op.setFilasAfectadas(filasVivas());
            op.setDetalle(huboErrores
                ? "Restaurado con avisos. pg_restore ignoro algun error; la salida completa: "
                  + recortar(salida)
                : "Restaurado desde " + r.getNombre() + " sin errores.");
            log.warn("RESTAURACION desde '{}' completada en {} ms",
                     r.getNombre(), System.currentTimeMillis() - inicio);
        } catch (Exception e) {
            op.setEstado(OperacionControl.FALLIDO);
            op.setDetalle(recortar(e.getMessage()));
            log.error("Restauracion desde '{}' fallida: {}", r.getNombre(), e.getMessage());
        } finally {
            mantenimiento.desactivar();
            op.setFechaFin(LocalDateTime.now());
            op.setDuracionMs(System.currentTimeMillis() - inicio);
            guardar(op);
            logService.registrarAparte(op.getIdUsuario(), "respaldos",
                    OperacionControl.COMPLETADO.equals(op.getEstado())
                        ? "restaurar" : "restaurar_fallido",
                    "Restauracion desde " + r.getNombre() + ": " + op.getEstado().toLowerCase(),
                    op.getIp());
        }
    }

    // =====================================================================
    // Fontaneria
    // =====================================================================

    private void exigirDisponible() {
        String problema = porQueNoSePuede();
        if (problema != null) {
            throw new ValidationException(problema);
        }
    }

    private void exigirConfirmacion(String escrito, String esperado) {
        if (escrito == null || !esperado.equals(escrito.trim())) {
            throw new ValidationException(
                "Para seguir hay que escribir exactamente: " + esperado);
        }
    }

    private void exigirTurnoLibre() {
        Tarea t = tarea.get();
        if (t != null) {
            throw new ValidationException(
                "Ya hay una operacion en curso (" + t.tipo.toLowerCase() + "). "
              + "Espera a que termine: hacer dos a la vez dejaria un respaldo de una base "
              + "a medio reemplazar, que es peor que no tenerlo.");
        }
    }

    private void tomarElTurno(Tarea t) {
        if (!tarea.compareAndSet(null, t)) {
            throw new ValidationException(
                "Ya hay una operacion en curso. Espera a que termine.");
        }
    }

    /** Bloquea hasta que el ejecutor se queda libre. Solo lo usa el borrado. */
    private void esperarAQueTermine() {
        long limite = System.currentTimeMillis() + Duration.ofMinutes(30).toMillis();
        while (tarea.get() != null && System.currentTimeMillis() < limite) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ValidationException("Espera interrumpida.");
            }
        }
        if (tarea.get() != null) {
            throw new ValidationException(
                "El respaldo previo lleva mas de 30 minutos. No se borra nada.");
        }
    }

    /**
     * Conexion propia como superusuario, fuera del pool de la aplicacion.
     *
     * <p>Hacen falta las dos cosas: <b>superusuario</b>, porque el TRUNCATE toca
     * tablas sobre las que {@code usr_admin_marathon} no tiene ese privilegio a
     * proposito; y <b>fuera del pool</b>, porque la conexion del pool es la que
     * esta atendiendo esta misma peticion y no puede vaciarse las tablas bajo
     * los pies.
     *
     * <p>Sin TLS: es una conexion a localhost desde el propio servidor, el mismo
     * criterio con el que se conectan los scripts de {@code scripts/backup/}.
     */
    private Connection conexionDeServicio() throws Exception {
        Properties props = new Properties();
        props.setProperty("user", usuario);
        props.setProperty("password", password);
        return DriverManager.getConnection(
                "jdbc:postgresql://" + host + ":" + puerto + "/" + base, props);
    }

    private Resultado ejecutar(String herramienta, String... argumentos)
            throws IOException, InterruptedException {
        return ejecutar(herramienta, null, argumentos);
    }

    /**
     * Ejecuta una herramienta de PostgreSQL y devuelve su codigo y su salida.
     *
     * @param porLinea si no es nulo, se le pasa cada linea segun sale. Es lo que
     *                 permite seguir el avance de {@code pg_restore} en vivo en
     *                 lugar de esperar a que acabe para leerlo todo de golpe.
     */
    private Resultado ejecutar(String herramienta, java.util.function.Consumer<String> porLinea,
                               String... argumentos) throws IOException, InterruptedException {
        List<String> comando = new ArrayList<>();
        File exe = new File(pgBin, herramienta + ".exe");
        comando.add(exe.isFile() ? exe.getAbsolutePath() : new File(pgBin, herramienta).getAbsolutePath());
        comando.addAll(List.of(argumentos));

        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.redirectErrorStream(true);
        // La contrasena va por el entorno del proceso hijo y NUNCA en la linea
        // de comandos: los argumentos de un proceso los ve cualquiera con el
        // Administrador de tareas o un `wmic process`.
        pb.environment().put("PGPASSWORD", password);
        // Sin esto, pg_dump escribe sus mensajes en el idioma del sistema y la
        // deteccion de "errors ignored on restore" dependeria del idioma.
        pb.environment().put("LC_MESSAGES", "C");

        Process p = pb.start();
        StringBuilder salida = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (porLinea != null) {
                    porLinea.accept(linea);
                    // Solo se recorta cuando hay consumidor, es decir en la
                    // corrida con --verbose: son miles de lineas y guardarlas
                    // todas llenaria la columna `detalle` del diario de ruido.
                    //
                    // El recorte NO puede aplicarse siempre, y costo un fallo
                    // real: `pg_restore --list` devuelve una linea por objeto, y
                    // contando sobre una salida recortada a 8 KB salian 100
                    // objetos en vez de 709. La barra de la restauracion se
                    // plantaba en el 99 % a los cuarenta segundos y ahi se
                    // quedaba cuatro minutos, que es justo lo que la barra venia
                    // a evitar.
                    if (salida.length() >= 8000) {
                        continue;
                    }
                }
                salida.append(linea).append('\n');
            }
        }
        int codigo = p.waitFor();
        return new Resultado(codigo, salida.toString().trim());
    }

    private record Resultado(int codigo, String salida) {}

    /**
     * Cuantos objetos hay que restaurar, segun el indice del propio volcado.
     *
     * <p>{@code pg_restore --list} lee la tabla de contenidos y no toca la base.
     * Cada linea util es un objeto —una tabla, un indice, una clave ajena, un
     * GRANT— y es exactamente lo que {@code --verbose} ira anunciando. Contar
     * aqui y contar alli mide lo mismo, que es lo que hace que el porcentaje
     * signifique algo.
     *
     * <p>Si falla, se devuelve 0 y la pantalla ensena un contador de segundos.
     * No poder estimar el avance no es motivo para no restaurar.
     */
    private long contarObjetos(String ruta) {
        try {
            Resultado res = ejecutar("pg_restore", "--list", ruta);
            if (res.codigo != 0) {
                return 0;
            }
            return res.salida.lines()
                    .filter(l -> !l.isBlank() && !l.startsWith(";"))
                    .count();
        } catch (Exception e) {
            log.warn("No se pudo leer el indice del respaldo para estimar el avance: {}", e.getMessage());
            return 0;
        }
    }

    private boolean existeEnDisco(Respaldo r) {
        return r.getRuta() != null && new File(r.getRuta()).isDirectory();
    }

    private long tamanoDe(Path carpeta) {
        try (Stream<Path> ficheros = Files.walk(carpeta)) {
            return ficheros.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Filas vivas segun el catalogo. Es una estimacion, y por eso no se cuenta. */
    private long filasVivas() {
        try {
            Query q = em.createNativeQuery(
                "SELECT coalesce(sum(n_live_tup), 0) FROM pg_stat_user_tables WHERE schemaname = 'public'");
            return ((Number) q.getSingleResult()).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    private String recortar(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 4000 ? s : s.substring(0, 4000) + "… (recortado)";
    }

    /**
     * Guarda en transaccion propia.
     *
     * <p>{@code REQUIRES_NEW} por la misma razon que
     * {@code LogService.registrarAparte}: estas filas se escriben desde un hilo
     * de fondo y desde caminos que pueden acabar lanzando excepcion. Compartir
     * la transaccion del llamador borraria el apunte justo en el caso que se
     * queria dejar registrado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Respaldo guardar(Respaldo r) {
        return respaldoRepository.saveAndFlush(r);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OperacionControl guardar(OperacionControl o) {
        return operacionRepository.saveAndFlush(o);
    }

    private RespaldoDTO aDTO(Respaldo r) {
        RespaldoDTO dto = new RespaldoDTO();
        dto.setIdRespaldo(r.getIdRespaldo());
        dto.setNombre(r.getNombre());
        dto.setOrigen(r.getOrigen());
        dto.setEstado(r.getEstado());
        dto.setFechaInicio(r.getFechaInicio());
        dto.setFechaFin(r.getFechaFin());
        dto.setDuracionMs(r.getDuracionMs());
        dto.setTamanoBytes(r.getTamanoBytes());
        dto.setFilas(r.getFilas());
        dto.setIdUsuario(r.getIdUsuario());
        dto.setUsuarioNombre(r.getUsuarioNombre());
        dto.setNota(r.getNota());
        dto.setMensaje(r.getMensaje());
        dto.setDisponible(Respaldo.COMPLETADO.equals(r.getEstado()) && existeEnDisco(r));
        return dto;
    }

    /** Lo consulta el programador de las 02:00 antes de decidir si tiene algo que hacer. */
    Optional<Respaldo> ultimoCompletado() {
        return respaldoRepository.ultimoCompletado();
    }

    boolean hayTareaEnCurso() {
        return tarea.get() != null;
    }

    // =====================================================================
    // La tarea en curso
    // =====================================================================

    /**
     * Lo que esta pasando, con su barra de progreso cuando se puede.
     *
     * <p>La estimacion del volcado sale de comparar los bytes ya escritos en la
     * carpeta con lo que ocupo el respaldo anterior. No es exacta —la base
     * crece— pero es honesta: se apoya en una medida real y no en un temporizador
     * inventado. La primera vez, sin respaldo anterior con el que comparar,
     * devuelve -1 y la pantalla ensena un contador de segundos en lugar de una
     * barra que no significaria nada.
     */
    private static final class Tarea {
        private final String tipo;
        private final String descripcion;
        private final long bytesEsperados;
        private final Path carpeta;
        private final long inicio = System.currentTimeMillis();
        private volatile String fase;
        private volatile long bytes;
        private volatile boolean vigilando;
        private Thread vigilante;

        /** Objetos del volcado y objetos ya procesados, para la restauracion. */
        private volatile long objetivo;
        private volatile long hechos;

        void fijarObjetivo(long cuantos) {
            this.objetivo = cuantos;
        }

        /**
         * Una linea de {@code pg_restore --verbose}.
         *
         * <p>Se cuentan solo las de «creating»: son las que anuncian un objeto
         * del indice y se corresponden una a una con lo que conto
         * {@code --list}. Las de «connecting», «processing» y las advertencias
         * hablan de otra cosa, y sumarlas haria que la barra pasara del 100 %.
         *
         * <p>La fase se actualiza con lo que se esta creando: durante los cuatro
         * o cinco minutos que dura, saber que va por los indices y no por los
         * datos es lo que distingue «avanza» de «se colgo».
         */
        void apuntarLinea(String linea) {
            if (linea == null) {
                return;
            }
            if (linea.contains("creating ")) {
                hechos++;
                int i = linea.indexOf("creating ");
                String que = linea.substring(i + 9).trim();
                int espacio = que.indexOf(' ');
                fase = "Restaurando: " + (espacio > 0 ? que.substring(0, espacio) : que).toLowerCase();
            }
        }

        Tarea(String tipo, String descripcion, long bytesEsperados, Path carpeta) {
            this.tipo = tipo;
            this.descripcion = descripcion;
            this.bytesEsperados = bytesEsperados;
            this.carpeta = carpeta;
            this.fase = descripcion;
        }

        void cambiarFase(String nueva) {
            this.fase = nueva;
        }

        void vigilarCarpeta() {
            if (carpeta == null) {
                return;
            }
            vigilando = true;
            vigilante = new Thread(() -> {
                while (vigilando) {
                    bytes = medir(carpeta);
                    try {
                        Thread.sleep(700);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "respaldos-progreso");
            vigilante.setDaemon(true);
            vigilante.start();
        }

        void detenerVigilancia() {
            vigilando = false;
            if (vigilante != null) {
                vigilante.interrupt();
            }
        }

        private static long medir(Path p) {
            if (!Files.exists(p)) {
                return 0;
            }
            try (Stream<Path> s = Files.walk(p)) {
                return s.filter(Files::isRegularFile).mapToLong(f -> {
                    try {
                        return Files.size(f);
                    } catch (IOException e) {
                        return 0L;
                    }
                }).sum();
            } catch (IOException e) {
                return 0;
            }
        }

        TareaEnCursoDTO aDTO() {
            TareaEnCursoDTO dto = new TareaEnCursoDTO();
            dto.setTipo(tipo);
            dto.setDescripcion(descripcion);
            dto.setFase(fase);
            dto.setSegundos((System.currentTimeMillis() - inicio) / 1000);
            dto.setBytes(bytes);
            dto.setBytesEsperados(bytesEsperados);
            // Tope en 99 en los dos casos: llegar al 100 lo decide el proceso al
            // terminar, no una division. Una barra clavada en el 100 % mientras
            // el sistema sigue parado es peor que una que se queda en 99.
            if (bytesEsperados > 0 && carpeta != null) {
                dto.setPorcentaje((int) Math.min(99, bytes * 100 / bytesEsperados));
            } else if (objetivo > 0) {
                dto.setPorcentaje((int) Math.min(99, hechos * 100 / objetivo));
            } else {
                dto.setPorcentaje(-1);
            }
            return dto;
        }
    }
}
