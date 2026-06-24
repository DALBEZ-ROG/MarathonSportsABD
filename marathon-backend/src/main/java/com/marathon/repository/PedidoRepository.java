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
