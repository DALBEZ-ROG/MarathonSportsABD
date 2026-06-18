package com.marathon.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Integer> {

    Page<Pedido> findByEstado(String estado, Pageable pageable);

    Page<Pedido> findByClienteIdCliente(Integer idCliente, Pageable pageable);

    Page<Pedido> findByClienteIdClienteAndEstado(Integer idCliente, String estado, Pageable pageable);

    Page<Pedido> findByFechaPedidoBetween(LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    Page<Pedido> findByEstadoAndFechaPedidoBetween(String estado, LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    long countByEstado(String estado);
}
