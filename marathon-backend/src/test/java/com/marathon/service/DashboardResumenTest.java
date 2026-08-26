package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.dashboard.DashboardResumenDTO;
import com.marathon.dto.dashboard.IndicadorDTO;
import com.marathon.soporte.FixturaVenta;

/**
 * D1 — cada indicador, contrastado contra los datos que la prueba misma crea.
 *
 * <p><b>Como se contrasta sin volver a escribir la consulta.</b> Repetir el SQL
 * del servicio dentro de la prueba no comprueba nada: si el {@code where} esta
 * mal, lo estara en los dos sitios y la prueba pasara igual. Aqui se usan tres
 * formas de contraste que no dependen de reescribir la consulta:
 *
 * <ul>
 *   <li><b>Diferencias.</b> Se lee el indicador, se crean N filas conocidas y se
 *       vuelve a leer: la diferencia tiene que ser exactamente N. Lo que se
 *       compara es contra lo que la prueba ha hecho, no contra otra copia del
 *       {@code select}.</li>
 *   <li><b>Particiones.</b> «Esperando picking» y «listos para empacar» tienen
 *       que sumar el total de pedidos en {@code procesado}. Son dos consultas
 *       distintas del servicio cuya suma es comprobable, asi que un
 *       {@code exists} mal puesto en cualquiera de las dos rompe la suma.</li>
 *   <li><b>Coherencia entre indicadores.</b> El denominador de la tasa de
 *       anulacion tiene que ser el mismo numero que «pedidos creados». Son dos
 *       consultas escritas por separado que deben coincidir.</li>
 * </ul>
 *
 * <p><b>Lo que esta prueba NO comprueba.</b> El perfil de pruebas desactiva el
 * enrutado por rol ({@code app.datasource.roles.enabled=false}), asi que todo se
 * ejecuta con {@code usr_admin_marathon}. Que a un operador de bodega le
 * funcione su tablero con <i>su</i> conexion no se demuestra ejecutandolo: se
 * comprueba preguntandole a la base si ese rol tiene permiso de lectura sobre
 * las tablas de su tablero ({@code losPermisosCubrenCadaTablero}).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("D1 - resumen del dashboard por rol")
class DashboardResumenTest {

    @Autowired private DashboardResumenService servicio;
    @Autowired private FixturaVenta fixtura;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void prepararDatos() {
        fixtura.empezar();
    }

    @AfterEach
    void borrarDatos() {
        fixtura.limpiar();
        SecurityContextHolder.clearContext();
    }

    // ==================================================================
    //  Contraste por diferencias
    // ==================================================================

    @Test
    @DisplayName("«pedidos creados» sube exactamente en los pedidos que crea la prueba")
    void pedidosCreadosCuentaLoQueSeCrea() {
        comoAdministrador();
        BigDecimal antes = valorDe("pedidos_creados");

        fixtura.pedidoListoParaEmpacar(1);
        fixtura.pedidoListoParaEmpacar(1);
        fixtura.pedidoConLineaSinRecoger(1);

        assertThat(valorDe("pedidos_creados").subtract(antes))
                .as("tres pedidos creados hoy tienen que entrar en la ventana de 30 dias")
                .isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("«listos para empacar» y «esperando picking» distinguen el picking completo")
    void elPickingSeparaLosDosIndicadores() {
        comoOperadorDeBodega();
        BigDecimal listosAntes = valorDe("listos_empacar");
        BigDecimal esperandoAntes = valorDe("esperando_picking");

        fixtura.pedidoListoParaEmpacar(1);          // todas las lineas recogidas
        fixtura.pedidoListoParaEmpacar(1);
        fixtura.pedidoConLineaSinRecoger(1);        // una linea sin recoger

        assertThat(valorDe("listos_empacar").subtract(listosAntes))
                .as("solo los dos con picking completo")
                .isEqualByComparingTo("2");
        assertThat(valorDe("esperando_picking").subtract(esperandoAntes))
                .as("solo el que tiene una linea sin recoger")
                .isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("«lineas por recoger» sube en las lineas pendientes, no en los pedidos")
    void lasLineasSeCuentanPorLinea() {
        comoOperadorDeBodega();
        IndicadorDTO antes = indicador(resumen(), "lineas_por_recoger");

        fixtura.pedidoConLineaSinRecoger(5);        // 1 linea de 5 unidades
        fixtura.pedidoListoParaEmpacar(3);          // 1 linea ya recogida

        IndicadorDTO ahora = indicador(resumen(), "lineas_por_recoger");

        assertThat(ahora.valor().subtract(antes.valor()))
                .as("una sola linea pendiente, por muchas unidades que lleve")
                .isEqualByComparingTo("1");
        assertThat(ahora.denominador().subtract(antes.denominador()))
                .as("el denominador son todas las lineas en picking: las dos")
                .isEqualByComparingTo("2");
    }

    // ==================================================================
    //  Contraste por particion y por coherencia
    // ==================================================================

    @Test
    @DisplayName("esperando picking + listos para empacar = todos los pedidos en procesado")
    void losDosIndicadoresParticionanElEstadoProcesado() {
        comoOperadorDeBodega();
        fixtura.pedidoListoParaEmpacar(1);
        fixtura.pedidoConLineaSinRecoger(1);

        DashboardResumenDTO r = resumen();
        BigDecimal suma = indicador(r, "esperando_picking").valor()
                .add(indicador(r, "listos_empacar").valor());

        BigDecimal enProcesado = jdbc.queryForObject(
                "select count(*) from pedido where estado = 'procesado'", BigDecimal.class);

        assertThat(suma)
                .as("un pedido en procesado o tiene lineas sin recoger o no las tiene: "
                  + "no hay tercera opcion, y ningun pedido puede contarse dos veces")
                .isEqualByComparingTo(enProcesado);
    }

    @Test
    @DisplayName("el denominador de la tasa de anulacion es el mismo numero que «pedidos creados»")
    void laTasaYLosCreadosCuadran() {
        comoAdministrador();
        fixtura.pedidoListoParaEmpacar(1);
        fixtura.pedidoListoParaEmpacar(1);

        DashboardResumenDTO r = resumen();

        assertThat(indicador(r, "tasa_anulacion").denominador())
                .as("son dos consultas escritas por separado sobre la misma ventana: "
                  + "si no coinciden, una de las dos tiene mal el filtro de fechas")
                .isEqualByComparingTo(indicador(r, "pedidos_creados").valor());
    }

    @Test
    @DisplayName("el numero de pedidos no anulados nunca supera al de creados")
    void losNoAnuladosCabenEnLosCreados() {
        comoAdministrador();
        fixtura.pedidoListoParaEmpacar(1);

        DashboardResumenDTO r = resumen();

        assertThat(indicador(r, "valor_pedido").denominador())
                .isLessThanOrEqualTo(indicador(r, "pedidos_creados").valor());
    }

    // ==================================================================
    //  Los seis tableros
    // ==================================================================

    @Test
    @DisplayName("cada rol recibe sus indicadores, y ninguno falla al calcularse")
    void cadaRolRecibeSuTablero() {
        comprobarTablero("ROLE_ADMINISTRADOR", "Administrador",
                "pedidos_creados", "valor_pedido", "tasa_anulacion", "pedidos_atascados",
                "ref_bajo_minimo", "cxp_vencidas", "ventas_entregadas");

        comprobarTablero("ROLE_SUPERVISOR E-COMMERCE", "Supervisor e-commerce",
                "pedidos_creados", "valor_pedido", "tasa_anulacion", "dias_despacho",
                "ventas_entregadas");

        comprobarTablero("ROLE_OPERADOR DE PEDIDOS", "Operador de pedidos",
                "mis_pedidos", "cola_pendiente", "dev_inspeccion", "especiales_abiertos");

        comprobarTablero("ROLE_OPERADOR DE BODEGA", "Operador de bodega",
                "esperando_picking", "lineas_por_recoger", "listos_empacar",
                "ref_bajo_minimo", "movimientos");

        comprobarTablero("ROLE_ENCARGADO DE COMPRAS", "Encargado de compras",
                "oc_pendientes", "oc_sin_recibir", "cxp_vencidas", "cxp_proximas",
                "dev_proveedor");

        comprobarTablero("ROLE_ENCARGADO DE PRODUCCIÓN", "Encargado de produccion",
                "op_en_proceso", "op_sin_material", "mp_bajo_minimo", "costo_medio_op",
                "merma_media");
    }

    @Test
    @DisplayName("el rol sale del token, no de la peticion")
    void elRolNoSeElige() {
        comoOperadorDeBodega();
        DashboardResumenDTO r = servicio.resumen("30d");

        assertThat(r.rol()).isEqualTo("OPERADOR DE BODEGA");
        assertThat(r.indicadores())
                .as("un operador de bodega no puede pedir el tablero del administrador "
                  + "cambiando la direccion, porque el rol no viaja en la direccion")
                .noneMatch(i -> "cxp_vencidas".equals(i.clave()));
    }

    @Test
    @DisplayName("quien es administrador ademas de otra cosa ve el tablero de administrador")
    void administradorMandaSobreElOtroRol() {
        autenticar("ROLE_OPERADOR DE BODEGA", "ROLE_ADMINISTRADOR");

        assertThat(servicio.resumen("30d").rol())
                .as("es el mismo criterio que usa el enrutado de conexiones (F37): si no "
                  + "coincidieran, se calcularia un tablero con la conexion de otro rol")
                .isEqualTo("ADMINISTRADOR");
    }

    // ==================================================================
    //  Los permisos de lectura y el reparto por rol son la misma lista
    // ==================================================================

    @Test
    @DisplayName("cada rol puede leer las tablas de su tablero")
    void losPermisosCubrenCadaTablero() {
        Map<String, List<String>> tablasPorRol = Map.of(
            "rol_administrador",        List.of("pedido", "inventario", "cuenta_por_pagar"),
            "rol_supervisor",           List.of("pedido", "detalle_pedido", "producto"),
            "rol_operador_pedidos",     List.of("pedido", "solicitud_devolucion"),
            "rol_operador_bodega",      List.of("pedido", "detalle_pedido", "inventario",
                                                "movimiento_inventario"),
            "rol_encargado_compras",    List.of("orden_compra", "cuenta_por_pagar",
                                                "devolucion_proveedor"),
            "rol_encargado_produccion", List.of("orden_produccion", "lista_materiales",
                                                "materia_prima"));

        tablasPorRol.forEach((rol, tablas) -> tablas.forEach(tabla ->
                assertThat(puedeLeer(rol, tabla))
                        .as("el tablero de %s consulta %s, asi que su rol de base de datos "
                          + "tiene que poder leerla", rol, tabla)
                        .isTrue()));

        // Control negativo: si estas dos dieran true, la comprobacion de arriba
        // no estaria comprobando nada — cualquier rol podria leerlo todo.
        assertThat(puedeLeer("rol_operador_bodega", "orden_produccion")).isFalse();
        assertThat(puedeLeer("rol_encargado_produccion", "pedido")).isFalse();
    }

    // ==================================================================
    //  Lo que no se puede calcular se dice
    // ==================================================================

    @Test
    @DisplayName("«ventas entregadas» se declara sin dato en vez de inventarse")
    void loQueNoSePuedeCalcularSeDice() {
        comoAdministrador();
        IndicadorDTO i = indicador(resumen(), "ventas_entregadas");

        assertThat(i.estado()).isEqualTo(IndicadorDTO.SIN_DATO);
        assertThat(i.valor())
                .as("rellenarlo con updated_at daria un numero que nadie podria auditar")
                .isNull();
        assertThat(i.nota()).contains("fecha de entrega");

        // Y el motivo es cierto: la columna no existe.
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema = 'public' and table_name = 'pedido'
                   and column_name like 'fecha_entrega%'
                """, Integer.class)).isZero();
    }

    @Test
    @DisplayName("la serie del grafico no tiene huecos: un dia sin pedidos vale cero")
    void laSerieVieneCompleta() {
        comoAdministrador();
        fixtura.pedidoListoParaEmpacar(1);

        DashboardResumenDTO r = servicio.resumen("7d");

        assertThat(r.serie())
                .as("siete dias, siete puntos: un hueco en el eje se leeria como "
                  + "«no se midio», y lo que pasa es que no hubo pedidos")
                .hasSize(7);
        assertThat(r.serie().get(0).dia()).isEqualTo(r.desde());
        assertThat(r.serie().get(6).dia()).isEqualTo(r.hasta());
        assertThat(r.serie()).allSatisfy(d -> {
            assertThat(d.pedidos()).isGreaterThanOrEqualTo(0);
            assertThat(d.importe()).isNotNull();
        });

        // El pedido de la fixtura es de hoy, asi que el ultimo punto no es cero.
        assertThat(r.serie().get(6).pedidos()).isPositive();
    }

    @Test
    @DisplayName("los roles operativos no reciben serie ni top: no es su tablero")
    void soloAdminYSupervisorRecibenGraficos() {
        comoOperadorDeBodega();
        DashboardResumenDTO r = resumen();

        assertThat(r.serie()).isEmpty();
        assertThat(r.topProductos()).isEmpty();
    }

    @Test
    @DisplayName("la respuesta lleva el periodo y la hora del calculo")
    void laRespuestaSeSabeFechar() {
        comoAdministrador();
        DashboardResumenDTO r = servicio.resumen("7d");

        assertThat(r.periodo()).isEqualTo("7d");
        assertThat(r.periodoEtiqueta()).contains("7");
        assertThat(r.desde().datesUntil(r.hasta().plusDays(1)).count()).isEqualTo(7);
        assertThat(r.generadoEn()).isNotNull();
        assertThat(r.indicadores()).allSatisfy(i ->
                assertThat(i.base())
                        .as("ninguna cifra puede salir sin decir de donde viene")
                        .isNotNull());
    }

    // ==================================================================
    //  Ayudas
    // ==================================================================

    private void comprobarTablero(String authority, String tituloEsperado, String... claves) {
        SecurityContextHolder.clearContext();
        autenticar(authority);
        DashboardResumenDTO r = servicio.resumen("30d");

        assertThat(r.indicadores())
                .extracting(IndicadorDTO::clave)
                .as("indicadores de %s", authority)
                .containsExactly(claves);

        assertThat(r.titulo()).isEqualTo("Tablero de " + tituloEsperado);

        assertThat(r.indicadores())
                .as("un indicador en error significa que la consulta no corrio; "
                  + "el detalle esta en el log del servidor")
                .noneMatch(i -> IndicadorDTO.ERROR.equals(i.estado()));

        assertThat(r.indicadores())
                .allSatisfy(i -> assertThat(i.periodo())
                        .as("%s sin periodo", i.clave())
                        .isNotBlank());
    }

    private boolean puedeLeer(String rol, String tabla) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select has_table_privilege(?, ?, 'SELECT')", Boolean.class, rol, tabla));
    }

    private DashboardResumenDTO resumen() {
        return servicio.resumen("30d");
    }

    private BigDecimal valorDe(String clave) {
        return indicador(resumen(), clave).valor();
    }

    private static IndicadorDTO indicador(DashboardResumenDTO r, String clave) {
        return r.indicadores().stream()
                .filter(i -> clave.equals(i.clave()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "el tablero de " + r.rol() + " no trae '" + clave + "'"));
    }

    private void comoAdministrador() {
        autenticar("ROLE_ADMINISTRADOR");
    }

    private void comoOperadorDeBodega() {
        autenticar("ROLE_OPERADOR DE BODEGA");
    }

    private static void autenticar(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("prueba", null,
                        java.util.Arrays.stream(authorities)
                                .map(SimpleGrantedAuthority::new)
                                .map(a -> (org.springframework.security.core.GrantedAuthority) a)
                                .toList()));
    }
}
