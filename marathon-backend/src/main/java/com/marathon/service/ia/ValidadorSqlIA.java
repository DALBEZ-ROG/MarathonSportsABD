package com.marathon.service.ia;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * Decide si el SQL que devolvio el modelo se puede ejecutar (L2, defecto D-04).
 *
 * <p>Sustituye a la comprobacion que habia en {@code IAService}, que buscaba
 * subcadenas prohibidas sobre el texto en mayusculas:
 *
 * <pre>
 *   String[] prohibidas = {"INSERT","UPDATE","DELETE","DROP","TRUNCATE","ALTER","CREATE"};
 *   if (upper.contains(palabra)) { rechazar(); }
 * </pre>
 *
 * <p>Aquello fallaba en las dos direcciones a la vez:
 *
 * <ul>
 *   <li><b>Dejaba pasar lo peligroso.</b> {@code DO}, {@code CALL}, {@code GRANT},
 *       {@code COPY} y {@code SET} no estaban en la lista, y un bloque
 *       {@code DO $$ BEGIN EXECUTE 'DEL'||'ETE FROM pedido'; END $$;} no contiene
 *       ninguna de las palabras vigiladas y escribe en la base.</li>
 *   <li><b>Bloqueaba lo inofensivo</b> (D-30). {@code CREATED_AT} contiene
 *       {@code CREATE} y {@code UPDATED_AT} contiene {@code UPDATE}, asi que
 *       cualquier consulta que pidiera una fecha de alta o de modificacion se
 *       rechazaba.</li>
 * </ul>
 *
 * <p>Ahora el texto se <b>analiza sintacticamente</b> en vez de compararse: si no
 * es exactamente una sentencia, y ademas un SELECT, y ademas sobre tablas de la
 * lista blanca, no se ejecuta.
 *
 * <p>Esta clase es la primera de dos barreras independientes. La segunda es la
 * transaccion de solo lectura de {@link EjecutorConsultaIA}: aunque un dia esta
 * validacion se dejara enganar, PostgreSQL seguiria rechazando la escritura.
 */
@Component
public class ValidadorSqlIA {

    /**
     * Tablas que el asistente puede leer.
     *
     * <p>Es una lista <b>blanca</b>, no negra: una tabla nueva queda fuera por
     * omision, que es el lado seguro por el que equivocarse.
     *
     * <p>Quedan deliberadamente fuera {@code usuario}, {@code rol},
     * {@code permiso}, {@code usuario_rol}, {@code rol_permiso},
     * {@code auditoria_cambios} y {@code log_accion}: son el modelo de seguridad
     * y la bitacora. Un Supervisor E-Commerce no tiene por que poder pedirle al
     * asistente los hashes de contrasena de sus companeros.
     */
    private static final Set<String> TABLAS_PERMITIDAS = Set.of(
            "bodega", "categoria", "ciudad", "cliente", "comprobante_interno",
            "cuenta_por_pagar", "detalle_pedido", "devolucion_proveedor",
            "devolucion_proveedor_detalle", "factura_compra", "historial_inventario",
            "inventario", "lista_materiales", "materia_prima", "movimiento_inventario",
            "movimiento_materia_prima", "orden_compra", "orden_compra_detalle",
            "orden_produccion", "orden_produccion_consumo", "pago_proveedor",
            "pedido", "producto", "producto_proveedor", "proveedor",
            "recepcion_mercancia", "recepcion_mercancia_detalle", "reembolso_cliente",
            "solicitud_devolucion", "solicitud_devolucion_detalle",
            // F84: sin estas dos, «cuanto mandamos por cada transportista» deja
            // de poder responderse, porque el pedido ya no guarda el nombre.
            "transportista", "transportista_cobertura",
            "unidad_medida");

    /** Resultado del analisis: o vale, o hay un motivo por el que no. */
    public record Veredicto(boolean permitido, String motivo) {

        public static Veredicto ok() {
            return new Veredicto(true, null);
        }

        public static Veredicto no(String motivo) {
            return new Veredicto(false, motivo);
        }
    }

    public Veredicto validar(String sql) {
        if (sql == null || sql.isBlank()) {
            return Veredicto.no("La consulta esta vacia.");
        }

        Statements sentencias;
        try {
            sentencias = CCJSqlParserUtil.parseStatements(sql);
        } catch (Exception e) {
            // Un texto que ni siquiera es SQL valido no se ejecuta. Aqui caen
            // tambien los bloques DO $$ ... $$, que no son una sentencia SQL.
            return Veredicto.no("La consulta no es SQL valido y no se ejecutara.");
        }

        List<Statement> lista = sentencias.getStatements();
        if (lista == null || lista.size() != 1) {
            return Veredicto.no("Solo se admite una sentencia por consulta; se recibieron "
                    + (lista == null ? 0 : lista.size()) + ".");
        }

        Statement sentencia = lista.get(0);
        if (!(sentencia instanceof Select)) {
            return Veredicto.no("Solo se admiten consultas de lectura (SELECT). "
                    + "Se recibio: " + sentencia.getClass().getSimpleName() + ".");
        }

        Set<String> usadas;
        try {
            usadas = new TablesNamesFinder().getTableList(sentencia).stream()
                    .map(t -> t.toLowerCase(Locale.ROOT))
                    // El modelo puede cualificar con el esquema: public.pedido
                    .map(t -> t.contains(".") ? t.substring(t.lastIndexOf('.') + 1) : t)
                    .map(t -> t.replace("\"", ""))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Veredicto.no("No se pudo determinar sobre que tablas consulta.");
        }

        Set<String> prohibidas = new TreeSet<>(usadas);
        prohibidas.removeAll(TABLAS_PERMITIDAS);
        if (!prohibidas.isEmpty()) {
            return Veredicto.no("La consulta usa tablas a las que el asistente no tiene acceso: "
                    + String.join(", ", prohibidas) + ".");
        }

        return Veredicto.ok();
    }
}
