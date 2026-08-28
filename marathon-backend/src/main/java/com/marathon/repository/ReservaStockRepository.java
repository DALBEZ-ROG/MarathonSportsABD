package com.marathon.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.ReservaStock;

public interface ReservaStockRepository extends JpaRepository<ReservaStock, Integer> {

    /**
     * Unidades activas de un producto, sea cual sea el pedido que las retiene.
     * Devuelve 0 y no null cuando no hay ninguna: el coalesce esta a proposito
     * para que quien llama no tenga que repetir la comprobacion en cada sitio.
     */
    @Query("SELECT COALESCE(SUM(r.cantidad), 0) FROM ReservaStock r "
         + "WHERE r.producto.idProducto = :idProducto AND r.estado = 'activa'")
    int reservadoDe(@Param("idProducto") Integer idProducto);

    /**
     * Lo mismo, excluyendo un pedido. Es la que usa el despacho: un pedido no
     * compite consigo mismo, sus propias unidades ya estan contadas en la
     * reserva que esta a punto de consumir.
     */
    @Query("SELECT COALESCE(SUM(r.cantidad), 0) FROM ReservaStock r "
         + "WHERE r.producto.idProducto = :idProducto AND r.estado = 'activa' "
         + "AND r.pedido.idPedido <> :idPedido")
    int reservadoDePorOtrosPedidos(@Param("idProducto") Integer idProducto,
                                   @Param("idPedido") Integer idPedido);

    /**
     * El JOIN FETCH no es decorativo: {@code ReservaStock.pedido} es LAZY, y el
     * informe construye su DTO fuera de transaccion. Sin el, la primera lectura
     * del estado del pedido revienta con LazyInitializationException.
     */
    @Query("SELECT r FROM ReservaStock r LEFT JOIN FETCH r.pedido LEFT JOIN FETCH r.producto "
         + "WHERE r.pedido.idPedido = :idPedido AND r.estado = 'activa'")
    List<ReservaStock> activasDePedido(@Param("idPedido") Integer idPedido);

    /**
     * Reservas que llevan mas tiempo del permitido reteniendo mercancia.
     *
     * <p>No las libera: las lista. La decision de negocio (2026-08-27) es
     * explicita en que soltar una reserva sin que nadie mire es peor que el
     * problema que resuelve.
     */
    @Query("SELECT r FROM ReservaStock r LEFT JOIN FETCH r.pedido LEFT JOIN FETCH r.producto "
         + "WHERE r.estado = 'activa' AND r.fechaReserva < :limite "
         + "ORDER BY r.fechaReserva ASC")
    List<ReservaStock> vencidasAntesDe(@Param("limite") LocalDateTime limite);

    long countByEstado(String estado);
}
