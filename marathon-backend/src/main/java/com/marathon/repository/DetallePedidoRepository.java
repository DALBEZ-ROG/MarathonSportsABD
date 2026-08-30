package com.marathon.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.marathon.model.DetallePedido;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Integer> {

    List<DetallePedido> findByPedidoIdPedido(Integer idPedido);

    List<DetallePedido> findByPedidoIdPedidoOrderByIdDetalleAsc(Integer idPedido);

    /** Detalles de VARIOS pedidos en una sola consulta (L16, D-28). */
    List<DetallePedido> findByPedidoIdPedidoIn(java.util.Collection<Integer> idsPedido);

    Long countByPedidoIdPedidoAndPickingCompletadoTrue(Integer idPedido);

    Long countByPedidoIdPedido(Integer idPedido);

    void deleteByPedidoIdPedido(Integer idPedido);

    @Query("SELECT COUNT(DISTINCT d.pedido.idPedido) FROM DetallePedido d "
            + "WHERE d.pedido.estado = 'procesado' AND d.pickingCompletado = false")
    Long contarPedidosPickingPendiente();

    /**
     * Los productos más vendidos, AGRUPANDO SOLO POR ID (F94).
     *
     * <p>Devuelve {@code [idProducto, unidades, ingresos]}. Los nombres los pone
     * el servicio después, para los diez que salen.
     *
     * <p><b>Por qué no se agrupa ya por el nombre.</b> Agrupar por
     * {@code d.producto.nombre} y {@code d.producto.categoria.nombre} obliga a
     * unir producto (1,5 millones de filas) y categoría a CADA UNA de las
     * 600.000 líneas entregadas <i>antes</i> de poder agrupar — para acabar
     * enseñando diez. Medido: 384 ms el agregado a secas, <b>1.291 ms</b> el
     * endpoint entero.
     *
     * <p>Agrupando por el id y buscando los diez nombres al final, los joins
     * pasan de 600.000 a 10.
     */
    @Query("SELECT d.producto.idProducto, SUM(d.cantidad), COALESCE(SUM(d.subtotal),0) "
            + "FROM DetallePedido d WHERE d.pedido.estado = 'entregado' "
            + "GROUP BY d.producto.idProducto "
            + "ORDER BY SUM(d.cantidad) DESC")
    List<Object[]> topProductosCrudo(Pageable pageable);
}
