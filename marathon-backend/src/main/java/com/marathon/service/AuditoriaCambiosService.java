package com.marathon.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.auditoria.CambioDatoDTO;
import com.marathon.dto.auditoria.RastroUsuarioDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * Lectura de {@code auditoria_cambios}, la bitácora campo a campo de la F40.
 *
 * <p><b>Por qué SQL nativo y no una entidad JPA.</b> No es pereza: la tabla
 * está declarada <i>append-only</i> a nivel de privilegios —ni el administrador
 * tiene UPDATE ni DELETE sobre ella (AUDITORIA.md §2)— y mapearla como entidad
 * la pondría al alcance de un {@code save()} accidental o de un
 * {@code cascade} desde otra entidad. Que no exista como entidad es parte de la
 * garantía. La F40 ya lo dejó escrito y aquí se respeta.
 *
 * <p>Todos los filtros se pasan como parámetros de sentencia preparada. Los
 * únicos trozos de SQL que se construyen concatenando son fragmentos fijos
 * escritos aquí; nada que venga del cliente entra en el texto de la consulta.
 */
@Service
public class AuditoriaCambiosService {

    /** El rango que se usa cuando no se pide fecha. El mismo criterio que LogService. */
    private static final LocalDateTime DESDE_SIEMPRE = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime HASTA_SIEMPRE = LocalDateTime.of(2999, 12, 31, 23, 59);

    @PersistenceContext
    private EntityManager em;

    // ------------------------------------------------------------------ listado

    /**
     * El listado con todos los filtros de la pantalla.
     *
     * <p>Cada parámetro nulo o vacío significa «no filtres por esto». Se
     * resuelve con un {@code WHERE} construido a trozos y no con el truco de
     * {@code (:x = 0 OR columna = :x)} que usa {@code LogAccionRepository}:
     * ese truco impide al planificador usar el índice, y sobre 1,5 millones de
     * filas la diferencia es un barrido secuencial completo por consulta.
     */
    public PageResponseDTO<CambioDatoDTO> listar(int page, int size,
                                                 Integer idUsuario, String tabla,
                                                 String operacion, String campo,
                                                 String pkValor, Long txid,
                                                 String texto,
                                                 LocalDateTime desde, LocalDateTime hasta) {

        List<String> condiciones = new ArrayList<>();
        List<Object> valores = new ArrayList<>();

        condiciones.add("a.fecha BETWEEN ?" + (valores.size() + 1) + " AND ?" + (valores.size() + 2));
        valores.add(desde != null ? desde : DESDE_SIEMPRE);
        valores.add(hasta != null ? hasta : HASTA_SIEMPRE);

        if (idUsuario != null) {
            condiciones.add("a.usuario_app = ?" + (valores.size() + 1));
            valores.add(idUsuario);
        }
        if (esUtil(tabla)) {
            condiciones.add("a.tabla = ?" + (valores.size() + 1));
            valores.add(tabla);
        }
        if (esUtil(operacion)) {
            condiciones.add("a.operacion = ?" + (valores.size() + 1));
            valores.add(operacion);
        }
        if (esUtil(campo)) {
            condiciones.add("a.campo = ?" + (valores.size() + 1));
            valores.add(campo);
        }
        if (esUtil(pkValor)) {
            condiciones.add("a.pk_valor = ?" + (valores.size() + 1));
            valores.add(pkValor);
        }
        if (txid != null) {
            condiciones.add("a.txid = ?" + (valores.size() + 1));
            valores.add(txid);
        }
        if (esUtil(texto)) {
            // Busca en los dos valores y en la clave de la fila. Es lo que
            // contesta «¿quién puso ESTE precio?» cuando se sabe el valor pero
            // no la fila. ILIKE, sin índice: es un filtro de última milla que se
            // aplica sobre un rango ya acotado por fecha o por tabla.
            int i = valores.size() + 1;
            condiciones.add("(a.valor_anterior ILIKE ?" + i
                          + " OR a.valor_nuevo ILIKE ?" + i
                          + " OR a.pk_valor ILIKE ?" + i + ")");
            valores.add("%" + texto.trim() + "%");
        }

        String where = " WHERE " + String.join(" AND ", condiciones);

        Query conteo = em.createNativeQuery("SELECT count(*) FROM auditoria_cambios a" + where);
        aplicar(conteo, valores);
        long total = ((Number) conteo.getSingleResult()).longValue();

        // El LEFT JOIN a usuario va DESPUES de paginar, sobre las 20 filas de la
        // página, no sobre el resultado entero. Con 1,5 millones de filas
        // auditadas y 1,5 millones de usuarios, juntar primero y recortar
        // después es la diferencia entre milisegundos y decenas de segundos.
        String sql =
            "SELECT p.id, p.fecha, p.tabla, p.pk_valor, p.operacion, p.campo, "
          + "       p.valor_anterior, p.valor_nuevo, p.usuario_bd, p.usuario_app, "
          + "       u.nombre, u.apellido, p.txid "
          + "FROM (SELECT a.* FROM auditoria_cambios a" + where
          + "      ORDER BY a.fecha DESC, a.id DESC "
          + "      LIMIT ?" + (valores.size() + 1) + " OFFSET ?" + (valores.size() + 2) + ") p "
          + "LEFT JOIN usuario u ON u.id_usuario = p.usuario_app "
          + "ORDER BY p.fecha DESC, p.id DESC";

        Query consulta = em.createNativeQuery(sql);
        aplicar(consulta, valores);
        consulta.setParameter(valores.size() + 1, size);
        consulta.setParameter(valores.size() + 2, (long) page * size);

        List<CambioDatoDTO> content = new ArrayList<>();
        for (Object fila : consulta.getResultList()) {
            content.add(aCambio((Object[]) fila));
        }

        int totalPaginas = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new PageResponseDTO<>(content, total, totalPaginas, page, size);
    }

    /** Todo lo que se cambió en una misma transacción: las N filas de un mismo acto. */
    public List<CambioDatoDTO> porTransaccion(long txid) {
        Query q = em.createNativeQuery(
                "SELECT a.id, a.fecha, a.tabla, a.pk_valor, a.operacion, a.campo, "
              + "       a.valor_anterior, a.valor_nuevo, a.usuario_bd, a.usuario_app, "
              + "       u.nombre, u.apellido, a.txid "
              + "FROM auditoria_cambios a "
              + "LEFT JOIN usuario u ON u.id_usuario = a.usuario_app "
              + "WHERE a.txid = ?1 ORDER BY a.id");
        q.setParameter(1, txid);

        List<CambioDatoDTO> lista = new ArrayList<>();
        for (Object fila : q.getResultList()) {
            lista.add(aCambio((Object[]) fila));
        }
        return lista;
    }

    // --------------------------------------------------------------- catálogos

    /**
     * Las tablas que tienen disparador de auditoría.
     *
     * <p>Se preguntan al catálogo del sistema, <b>no</b> con un
     * {@code SELECT DISTINCT tabla FROM auditoria_cambios}: ese distinct son 90
     * ms de barrido sobre 1,5 millones de filas, y además solo devolvería las
     * tablas que ya tienen algún cambio registrado. Una tabla recién auditada,
     * todavía sin cambios, desaparecería del desplegable justo cuando alguien
     * quiere comprobar que la están auditando.
     */
    public List<String> tablasAuditadas() {
        // cast(... as text) y NO el `::text` de PostgreSQL: Hibernate analiza el
        // texto de la consulta nativa buscando parametros con nombre, y se come
        // el `:text` como si lo fuera. El error que sale —«error de sintaxis en
        // o cerca de :»— no menciona a Hibernate por ningun lado y manda a
        // buscar el fallo en el SQL, que es correcto.
        List<?> filas = em.createNativeQuery(
                "SELECT DISTINCT cast(c.relname AS text) FROM pg_trigger t "
              + "JOIN pg_class c ON c.oid = t.tgrelid "
              + "WHERE t.tgname LIKE 'trg_auditoria_%' AND NOT t.tgisinternal "
              + "ORDER BY 1").getResultList();

        List<String> tablas = new ArrayList<>();
        for (Object fila : filas) {
            tablas.add((String) fila);
        }
        return tablas;
    }

    /** Los campos que han cambiado alguna vez en una tabla, para el desplegable. */
    public List<String> camposDe(String tabla) {
        if (!esUtil(tabla)) {
            return List.of();
        }
        Query q = em.createNativeQuery(
                "SELECT DISTINCT campo FROM auditoria_cambios "
              + "WHERE tabla = ?1 AND campo IS NOT NULL ORDER BY 1");
        q.setParameter(1, tabla);

        List<String> campos = new ArrayList<>();
        for (Object fila : q.getResultList()) {
            campos.add((String) fila);
        }
        return campos;
    }

    // ------------------------------------------------------------------ rastro

    /**
     * «¿En qué partes del sistema tocó algo esta persona, y qué tocó?»
     *
     * <p>Cruza las tres bitácoras. No las fusiona en una sola lista ordenada por
     * fecha: un UNION ALL paginado sobre tres tablas de más de un millón de
     * filas obliga a materializar y ordenar las tres enteras para poder dar la
     * primera página. Se devuelven <b>recuentos por sitio</b>, que es lo que
     * contesta la pregunta, y desde cada línea la pantalla salta a la pestaña de
     * detalle ya filtrada — que sí usa índice.
     */
    @Transactional(readOnly = true)
    public RastroUsuarioDTO rastroDeUsuario(Integer idUsuario, LocalDateTime desde, LocalDateTime hasta) {
        LocalDateTime d = desde != null ? desde : DESDE_SIEMPRE;
        LocalDateTime h = hasta != null ? hasta : HASTA_SIEMPRE;

        RastroUsuarioDTO dto = new RastroUsuarioDTO();
        dto.setIdUsuario(idUsuario);

        Query nombre = em.createNativeQuery(
                "SELECT nombre || ' ' || apellido FROM usuario WHERE id_usuario = ?1");
        nombre.setParameter(1, idUsuario);
        List<?> nombres = nombre.getResultList();
        dto.setUsuarioNombre(nombres.isEmpty() ? null : (String) nombres.get(0));

        // --- qué HIZO, por módulo y acción (log_accion) ---
        Query porModulo = em.createNativeQuery(
                "SELECT modulo, accion, count(*), min(fecha), max(fecha) "
              + "FROM log_accion WHERE id_usuario = ?1 AND fecha BETWEEN ?2 AND ?3 "
              + "GROUP BY modulo, accion ORDER BY count(*) DESC, modulo, accion");
        porModulo.setParameter(1, idUsuario);
        porModulo.setParameter(2, d);
        porModulo.setParameter(3, h);
        dto.setPorModulo(aLineas(porModulo.getResultList()));

        // --- qué DATO cambió, por tabla y operación (auditoria_cambios) ---
        Query porTabla = em.createNativeQuery(
                "SELECT tabla, operacion, count(*), min(fecha), max(fecha) "
              + "FROM auditoria_cambios WHERE usuario_app = ?1 AND fecha BETWEEN ?2 AND ?3 "
              + "GROUP BY tabla, operacion ORDER BY count(*) DESC, tabla, operacion");
        porTabla.setParameter(1, idUsuario);
        porTabla.setParameter(2, d);
        porTabla.setParameter(3, h);
        dto.setPorTabla(aLineas(porTabla.getResultList()));

        // --- cuánto stock MOVIO y dónde (historial_inventario) ---
        Query porBodega = em.createNativeQuery(
                "SELECT b.nombre, hi.motivo, count(*), min(hi.fecha), max(hi.fecha) "
              + "FROM historial_inventario hi "
              + "JOIN inventario i ON i.id_inventario = hi.id_inventario "
              + "JOIN bodega b     ON b.id_bodega = i.id_bodega "
              + "WHERE hi.id_usuario = ?1 AND hi.fecha BETWEEN ?2 AND ?3 "
              + "GROUP BY b.nombre, hi.motivo ORDER BY count(*) DESC, b.nombre");
        porBodega.setParameter(1, idUsuario);
        porBodega.setParameter(2, d);
        porBodega.setParameter(3, h);
        dto.setPorBodega(aLineas(porBodega.getResultList()));

        long acciones = dto.getPorModulo().stream().mapToLong(RastroUsuarioDTO.Linea::getVeces).sum();
        long cambios = dto.getPorTabla().stream().mapToLong(RastroUsuarioDTO.Linea::getVeces).sum();
        long movimientos = dto.getPorBodega().stream().mapToLong(RastroUsuarioDTO.Linea::getVeces).sum();
        dto.setTotalAcciones(acciones);
        dto.setTotalCambios(cambios);
        dto.setTotalMovimientos(movimientos);

        dto.setPrimeraHuella(extremo(dto, true));
        dto.setUltimaHuella(extremo(dto, false));
        return dto;
    }

    // ------------------------------------------------------------------ ayudas

    private LocalDateTime extremo(RastroUsuarioDTO dto, boolean minima) {
        LocalDateTime resultado = null;
        List<List<RastroUsuarioDTO.Linea>> todas =
                List.of(dto.getPorModulo(), dto.getPorTabla(), dto.getPorBodega());
        for (List<RastroUsuarioDTO.Linea> lista : todas) {
            for (RastroUsuarioDTO.Linea l : lista) {
                LocalDateTime candidata = minima ? l.getPrimera() : l.getUltima();
                if (candidata == null) {
                    continue;
                }
                if (resultado == null
                        || (minima ? candidata.isBefore(resultado) : candidata.isAfter(resultado))) {
                    resultado = candidata;
                }
            }
        }
        return resultado;
    }

    private List<RastroUsuarioDTO.Linea> aLineas(List<?> filas) {
        List<RastroUsuarioDTO.Linea> lineas = new ArrayList<>();
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            lineas.add(new RastroUsuarioDTO.Linea(
                    (String) f[0],
                    (String) f[1],
                    ((Number) f[2]).longValue(),
                    aFecha(f[3]),
                    aFecha(f[4])));
        }
        return lineas;
    }

    private CambioDatoDTO aCambio(Object[] f) {
        CambioDatoDTO dto = new CambioDatoDTO();
        dto.setId(((Number) f[0]).longValue());
        dto.setFecha(aFecha(f[1]));
        dto.setTabla((String) f[2]);
        dto.setPkValor((String) f[3]);
        dto.setOperacion((String) f[4]);
        dto.setCampo((String) f[5]);
        dto.setValorAnterior((String) f[6]);
        dto.setValorNuevo((String) f[7]);
        dto.setUsuarioBd((String) f[8]);
        dto.setIdUsuario(f[9] != null ? ((Number) f[9]).intValue() : null);
        if (f[10] != null) {
            dto.setUsuarioNombre(f[10] + (f[11] != null ? " " + f[11] : ""));
        }
        dto.setTxid(f[12] != null ? ((Number) f[12]).longValue() : null);
        return dto;
    }

    private LocalDateTime aFecha(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        return (LocalDateTime) valor;
    }

    private void aplicar(Query q, List<Object> valores) {
        for (int i = 0; i < valores.size(); i++) {
            q.setParameter(i + 1, valores.get(i));
        }
    }

    private boolean esUtil(String s) {
        return s != null && !s.isBlank();
    }
}
