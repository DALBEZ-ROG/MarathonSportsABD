package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.DetallePedido;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Integer> {

    List<DetallePedido> findByPedidoIdPedido(Integer idPedido);

    List<DetallePedido> findByPedidoIdPedidoOrderByIdDetalleAsc(Integer idPedido);

    Long countByPedidoIdPedidoAndPickingCompletadoTrue(Integer idPedido);

    Long countByPedidoIdPedido(Integer idPedido);

    void deleteByPedidoIdPedido(Integer idPedido);
}
