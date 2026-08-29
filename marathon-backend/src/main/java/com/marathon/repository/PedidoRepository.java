package com.marathon.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.dto.dashboard.EstadoPedidoDTO;
import com.marathon.dto.dashboard.VentaDiaDTO;
import com.marathon.model.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Integer> {

    Page<Pedido> findByEstado(String estado, Pageable pageable);

    /**
     * Pedidos listos para empacar: en {@code procesado} y con el picking
     * COMPLETO (F52, D-42).
     *
     * <p>La pantalla de Empaque pedia los 100 primeros pedidos procesados y
     * filtraba en el navegador los que tenian el picking completo. Con 19.059
     * pedidos en {@code procesado} ordenados del mas antiguo, un pedido que se
     * acaba de recoger queda el ultimo de la cola: estaba en la posicion 19.059
     * de una lista de la que solo se traian 100, asi que <b>no aparecia
     * nunca</b>. Quien recogia un pedido no podia empacarlo.
     *
     * <p>Ahora el filtro lo hace la base y la pantalla pagina de verdad.
     *
     * <p>El {@code EXISTS} no sobra: sin el, un pedido <i>sin lineas</i>
     * cumpliria el {@code NOT EXISTS} por vacuidad y saldria listado como listo
     * para empacar.
     */
    @Query("SELECT p FROM Pedido p WHERE p.estado = 'procesado' "
         + "AND EXISTS (SELECT 1 FROM DetallePedido d WHERE d.pedido = p) "
         + "AND NOT EXISTS (SELECT 1 FROM DetallePedido d WHERE d.pedido = p "
         + "                AND d.pickingCompletado = false)")
    Page<Pedido> buscarListosParaEmpacar(Pageable pageable);

    /**
     * Listado de pedidos con TODOS los filtros en una sola consulta, incluida
     * la busqueda por texto (F54).
     *
     * <p>Sustituye a las cuatro ramas if/else que tenia PedidoService.listar,
     * que no admitian buscar: con 230.000 pedidos y 10 por pagina, encontrar
     * uno concreto significaba pasar paginas. Cada filtro es opcional y se
     * anula solo cuando llega a null, asi que las combinaciones no multiplican
     * metodos.
     *
     * <p>El texto busca por <b>numero de pedido</b> y por <b>nombre o apellido
     * del cliente</b>. Se puede buscar por nombre porque `nombre` y `apellido`
     * estan en claro; los datos de contacto (correo, telefono, direccion) si
     * estan cifrados (F41) y por eso no se buscan aqui — un LIKE sobre un
     * bytea cifrado no encontraria nada.
     *
     * <p><b>Las fechas NO se anulan con IS NULL</b>, al reves que el estado y
     * el texto: se pasan siempre, con topes por defecto que abarcan todo. Si se
     * escribiera {@code (:desde IS NULL OR ...)}, PostgreSQL no puede deducir
     * el tipo de un parametro nulo que solo aparece en un {@code ? IS NULL} y
     * la consulta muere con «no se pudo determinar el tipo del parametro». Es
     * el mismo motivo por el que {@link #findDespachados} recibe siempre sus
     * dos fechas, y por el que el texto lleva un {@code CAST(... AS string)}:
     * un parametro nulo necesita que alguien le diga de que tipo es.
     */
    @Query("SELECT p FROM Pedido p WHERE "
         + "(:estado IS NULL OR p.estado = :estado) "
         + "AND p.fechaPedido >= :desde "
         + "AND p.fechaPedido <= :hasta "
         + "AND (:texto IS NULL "
         + "     OR CAST(p.idPedido AS string) LIKE CONCAT('%', CAST(:texto AS string), '%') "
         + "     OR LOWER(p.cliente.nombre) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) "
         + "     OR LOWER(p.cliente.apellido) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')))")
    Page<Pedido> buscar(@Param("estado") String estado,
                        @Param("desde") LocalDateTime desde,
                        @Param("hasta") LocalDateTime hasta,
                        @Param("texto") String texto,
                        Pageable pageable);

    Page<Pedido> findByClienteIdCliente(Integer idCliente, Pageable pageable);

    Page<Pedido> findByClienteIdClienteAndEstado(Integer idCliente, String estado, Pageable pageable);

    Page<Pedido> findByFechaPedidoBetween(LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    Page<Pedido> findByEstadoAndFechaPedidoBetween(String estado, LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    Page<Pedido> findByEsPedidoEspecialTrue(Pageable pageable);

    Page<Pedido> findByEsPedidoEspecialTrueAndTipoEspecial(String tipoEspecial, Pageable pageable);

    Long countByEsPedidoEspecialTrueAndEstadoNot(String estado);

    long countByEstado(String estado);

    // F84: el filtro por region ya no mira una columna del pedido —no existe—,
    // sino la region de la ciudad del cliente, que es de donde salia el dato.
    //
    // El LEFT JOIN FETCH del transportista tampoco es adorno: sin el, Hibernate
    // pedia el transportista aparte, una vez por cada uno DISTINTO de la pagina.
    // Con un solo transportista es una consulta de mas; con diez, diez. Lo cazo
    // RendimientoDespachosTest, que cuenta consultas en vez de medir tiempo.
    @Query(value = "SELECT p FROM Pedido p LEFT JOIN FETCH p.transportista "
            + "WHERE p.estado IN ('enviado','entregado') "
            + "AND (:region = '' OR p.cliente.ciudad.region = :region) "
            + "AND p.fechaEmpaque >= :desde "
            + "AND p.fechaEmpaque <= :hasta "
            + "ORDER BY p.fechaEmpaque DESC, p.idPedido DESC",
           // La de contar va escrita a mano: derivarla de una consulta con JOIN
           // FETCH no siempre sale bien, y un COUNT no necesita traer nada.
           countQuery = "SELECT COUNT(p) FROM Pedido p "
            + "WHERE p.estado IN ('enviado','entregado') "
            + "AND (:region = '' OR p.cliente.ciudad.region = :region) "
            + "AND p.fechaEmpaque >= :desde "
            + "AND p.fechaEmpaque <= :hasta")
    Page<Pedido> findDespachados(@Param("region") String region,
                                 @Param("desde") LocalDateTime desde,
                                 @Param("hasta") LocalDateTime hasta,
                                 Pageable pageable);

    @Query("SELECT COUNT(p) FROM Pedido p WHERE CAST(p.fechaPedido AS LocalDate) = CURRENT_DATE")
    Long contarPedidosHoy();

    @Query("SELECT COALESCE(SUM(p.total),0) FROM Pedido p WHERE p.estado = 'entregado' "
            + "AND CAST(p.fechaPedido AS LocalDate) = CURRENT_DATE")
    BigDecimal totalVentasHoy();

    @Query("SELECT COALESCE(SUM(p.total),0) FROM Pedido p WHERE p.estado = 'entregado' "
            + "AND EXTRACT(YEAR FROM p.fechaPedido) = EXTRACT(YEAR FROM CURRENT_DATE) "
            + "AND EXTRACT(MONTH FROM p.fechaPedido) = EXTRACT(MONTH FROM CURRENT_DATE)")
    BigDecimal totalVentasMes();

    @Query("SELECT new com.marathon.dto.dashboard.VentaDiaDTO(CAST(p.fechaPedido AS LocalDate), "
            + "COALESCE(SUM(p.total),0), COUNT(p)) FROM Pedido p "
            + "WHERE p.estado = 'entregado' AND p.fechaPedido >= :desde "
            + "GROUP BY CAST(p.fechaPedido AS LocalDate) "
            + "ORDER BY CAST(p.fechaPedido AS LocalDate) ASC")
    List<VentaDiaDTO> ventasPorDia(@Param("desde") LocalDateTime desde);

    @Query("SELECT new com.marathon.dto.dashboard.EstadoPedidoDTO(p.estado, COUNT(p)) "
            + "FROM Pedido p GROUP BY p.estado")
    List<EstadoPedidoDTO> pedidosPorEstado();
}
