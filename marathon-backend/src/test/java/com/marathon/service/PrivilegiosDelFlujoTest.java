package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * F65 — los privilegios que cada rol necesita para <b>terminar</b> su parte del
 * flujo, no solo para verla.
 *
 * <p><b>De dónde sale esta clase.</b> El Encargado de Compras aprobaba la orden,
 * entraba a registrar la recepción y recibía <i>«Tu rol no tiene permisos sobre
 * estos datos»</i>. La matriz de permisos estaba bien; quien denegaba era
 * PostgreSQL, por dos GRANT que nunca se concedieron. El Encargado de Producción
 * tenía el mismo problema y aún no lo había descubierto nadie.
 *
 * <p><b>Por qué no lo vieron las pruebas ni el barrido.</b> Es la misma trampa
 * que escondió D-39 desde la F37, y reaparece por el mismo motivo: el perfil de
 * pruebas usa un único pool ({@code app.datasource.roles.enabled=false}), así
 * que el arnés <b>nunca</b> se conecta como {@code rol_encargado_compras}. Y el
 * barrido de la F63 recorrió 128 pantallas, pero solo con GET: cargar la
 * pantalla de recepción funciona, lo que falla es enviarla.
 *
 * <p>Estas pruebas no ejecutan el flujo — no pueden, por lo anterior —. Lo que
 * hacen es <b>leer los privilegios reales de PostgreSQL</b> y comprobar que
 * están los que el código necesita. Es la única forma de que el arnés vigile
 * esta clase de defecto.
 *
 * <p>Se lee con {@code aclexplode()} sobre {@code pg_class} y {@code pg_attribute},
 * y no con {@code information_schema}: para {@code usr_admin_marathon} esas
 * vistas devuelven vacío y harían pasar la prueba sin comprobar nada
 * (PENDIENTE.md §2, punto 7).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F65 · privilegios de escritura del flujo")
class PrivilegiosDelFlujoTest {

    @Autowired private JdbcTemplate jdbc;

    private List<String> privilegiosDeTabla(String tabla, String rol) {
        return jdbc.queryForList(
                "SELECT a.privilege_type FROM pg_class c, aclexplode(c.relacl) a "
                + "WHERE c.relname = ? AND a.grantee::regrole::text = ?",
                String.class, tabla, rol);
    }

    private List<String> privilegiosDeColumna(String tabla, String columna, String rol) {
        return jdbc.queryForList(
                "SELECT a.privilege_type FROM pg_class c "
                + "JOIN pg_attribute at ON at.attrelid = c.oid AND at.attnum > 0 "
                + "CROSS JOIN LATERAL aclexplode(at.attacl) a "
                + "WHERE c.relname = ? AND at.attname = ? AND a.grantee::regrole::text = ?",
                String.class, tabla, columna, rol);
    }

    @Test
    @DisplayName("quien escribe un movimiento de inventario también puede leerlo")
    void quienEscribeMovimientosPuedeLeerlos() {
        // No es un capricho: la clave primaria es IDENTITY, así que Hibernate
        // emite INSERT ... RETURNING id_movimiento, y RETURNING exige SELECT.
        // Con INSERT a secas PostgreSQL rechaza la sentencia entera — que es
        // justo lo que rompía la recepción de mercancía.
        for (String rol : List.of("rol_encargado_compras", "rol_encargado_produccion",
                                  "rol_operador_bodega")) {
            assertThat(privilegiosDeTabla("movimiento_inventario", rol))
                    .as("%s escribe movimientos de inventario; sin SELECT el "
                        + "INSERT ... RETURNING de Hibernate falla entero", rol)
                    .contains("INSERT", "SELECT");
        }
    }

    @Test
    @DisplayName("un movimiento de inventario no lo corrige nadie salvo el administrador")
    void unMovimientoNoSeCorrige() {
        // La otra mitad de la prueba anterior: conceder SELECT no puede haberse
        // convertido en conceder de más. Un movimiento es un asiento.
        for (String rol : List.of("rol_encargado_compras", "rol_encargado_produccion",
                                  "rol_operador_bodega", "rol_supervisor",
                                  "rol_operador_pedidos")) {
            assertThat(privilegiosDeTabla("movimiento_inventario", rol))
                    .as("%s no debe poder reescribir ni borrar un movimiento ya registrado", rol)
                    .doesNotContain("UPDATE", "DELETE");
        }
    }

    @Test
    @DisplayName("Producción puede escribir el costo del consumo al iniciar la orden")
    void produccionEscribeElSnapshotDeCosto() {
        // La F29 fotografía el costo promedio AL INICIAR, no al planificar: la
        // fila se crea en crear() y esta columna se rellena en iniciar(). Es el
        // caso de manual de una tabla que se llena por etapas.
        assertThat(privilegiosDeColumna("orden_produccion_consumo",
                                        "costo_unitario_snapshot", "rol_encargado_produccion"))
                .as("sin esto, iniciar una orden de producción devuelve un 500")
                .contains("UPDATE");
    }

    @Test
    @DisplayName("...pero no puede reescribir lo que se planificó")
    void produccionNoReescribeLoPlanificado() {
        for (String columna : List.of("cantidad_teorica", "id_materia_prima", "id_orden_produccion")) {
            assertThat(privilegiosDeColumna("orden_produccion_consumo", columna,
                                            "rol_encargado_produccion"))
                    .as("%s es de la etapa de planificación y no se reescribe", columna)
                    .doesNotContain("UPDATE");
        }
    }

    @Test
    @DisplayName("un pago a proveedor se registra, y no se corrige")
    void unPagoNoSeCorrige() {
        // Por esto NO se concedió UPDATE cuando el pago fallaba: se arregló el
        // código para escribir una sola vez, en vez de dejar que Compras
        // pudiera cambiar el importe de un pago ya registrado.
        assertThat(privilegiosDeTabla("pago_proveedor", "rol_encargado_compras"))
                .as("Compras registra pagos...")
                .contains("INSERT", "SELECT");
        assertThat(privilegiosDeTabla("pago_proveedor", "rol_encargado_compras"))
                .as("...y no los reescribe: es un asiento contable")
                .doesNotContain("UPDATE", "DELETE");
    }

    @Test
    @DisplayName("Compras puede cerrar su cadena: recibir, facturar y pagar")
    void comprasPuedeCerrarSuCadena() {
        assertThat(privilegiosDeTabla("recepcion_mercancia", "rol_encargado_compras"))
                .contains("INSERT", "SELECT");
        assertThat(privilegiosDeTabla("recepcion_mercancia_detalle", "rol_encargado_compras"))
                .contains("INSERT", "SELECT");
        // El stock que entra con la recepción.
        assertThat(privilegiosDeColumna("inventario", "stock_actual", "rol_encargado_compras"))
                .as("recibir mercancía sube el stock de la bodega")
                .contains("UPDATE");
        // Lo recibido en la línea de la orden, y el estado de la orden.
        assertThat(privilegiosDeColumna("orden_compra_detalle", "cantidad_recibida",
                                        "rol_encargado_compras"))
                .contains("UPDATE");
        assertThat(privilegiosDeColumna("orden_compra", "estado", "rol_encargado_compras"))
                .as("la recepción deja la orden en recibida_parcial o recibida_completa")
                .contains("UPDATE");
    }
}
