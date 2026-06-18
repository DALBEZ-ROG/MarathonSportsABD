package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.DetallePedido;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Integer> {

    List<DetallePedido> findByPedidoIdPedido(Integer idPedido);

    void deleteByPedidoIdPedido(Integer idPedido);
}
