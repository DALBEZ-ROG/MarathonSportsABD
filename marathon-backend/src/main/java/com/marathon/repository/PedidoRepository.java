package com.marathon.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Integer> {

    Page<Pedido> findByEstado(String estado, Pageable pageable);

    Page<Pedido> findByClienteIdCliente(Integer idCliente, Pageable pageable);

    Page<Pedido> findByClienteIdClienteAndEstado(Integer idCliente, String estado, Pageable pageable);

    Page<Pedido> findByFechaPedidoBetween(LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    Page<Pedido> findByEstadoAndFechaPedidoBetween(String estado, LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    Page<Pedido> findByEsPedidoEspecialTrue(Pageable pageable);

    Page<Pedido> findByEsPedidoEspecialTrueAndTipoEspecial(String tipoEspecial, Pageable pageable);

    Long countByEsPedidoEspecialTrueAndEstadoNot(String estado);

    long countByEstado(String estado);

    @Query("SELECT p FROM Pedido p WHERE p.estado IN ('enviado','entregado') "
            + "AND (:region = '' OR p.regionDestino = :region) "
            + "AND p.fechaEmpaque >= :desde "
            + "AND p.fechaEmpaque <= :hasta "
            + "ORDER BY p.fechaEmpaque DESC")
    Page<Pedido> findDespachados(@Param("region") String region,
                                 @Param("desde") LocalDateTime desde,
                                 @Param("hasta") LocalDateTime hasta,
                                 Pageable pageable);
}
