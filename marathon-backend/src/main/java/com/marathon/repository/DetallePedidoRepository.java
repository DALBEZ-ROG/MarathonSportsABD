package com.marathon.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.marathon.dto.dashboard.TopProductoDTO;
import com.marathon.model.DetallePedido;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Integer> {

    List<DetallePedido> findByPedidoIdPedido(Integer idPedido);

    List<DetallePedido> findByPedidoIdPedidoOrderByIdDetalleAsc(Integer idPedido);

    Long countByPedidoIdPedidoAndPickingCompletadoTrue(Integer idPedido);

    Long countByPedidoIdPedido(Integer idPedido);

    void deleteByPedidoIdPedido(Integer idPedido);

    @Query("SELECT COUNT(DISTINCT d.pedido.idPedido) FROM DetallePedido d "
            + "WHERE d.pedido.estado = 'procesado' AND d.pickingCompletado = false")
    Long contarPedidosPickingPendiente();

    @Query("SELECT new com.marathon.dto.dashboard.TopProductoDTO(d.producto.idProducto, "
            + "d.producto.nombre, d.producto.categoria.nombre, SUM(d.cantidad), COALESCE(SUM(d.subtotal),0)) "
            + "FROM DetallePedido d WHERE d.pedido.estado = 'entregado' "
            + "GROUP BY d.producto.idProducto, d.producto.nombre, d.producto.categoria.nombre "
            + "ORDER BY SUM(d.cantidad) DESC")
    List<TopProductoDTO> topProductos(Pageable pageable);
}
