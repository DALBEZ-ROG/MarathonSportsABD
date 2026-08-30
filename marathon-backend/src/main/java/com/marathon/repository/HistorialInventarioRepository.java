package com.marathon.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.HistorialInventario;

public interface HistorialInventarioRepository extends JpaRepository<HistorialInventario, Integer> {

    List<HistorialInventario> findByInventarioIdInventarioOrderByFechaDesc(Integer idInventario);

    // F94 — EXISTS. Nombrar `h.inventario.producto` y `h.inventario.bodega`
    // metía dos joins en el FROM que se pagaban siempre, incluso al abrir la
    // pestaña sin filtrar por ninguno de los dos — que es como se abre.
    @Query("SELECT h FROM HistorialInventario h WHERE "
            + "(:idProducto = 0 OR EXISTS (SELECT 1 FROM Inventario iv WHERE iv = h.inventario "
            + "                              AND iv.producto.idProducto = :idProducto)) "
            + "AND (:idBodega = 0 OR EXISTS (SELECT 1 FROM Inventario iv2 WHERE iv2 = h.inventario "
            + "                                AND iv2.bodega.idBodega = :idBodega)) "
            + "AND h.fecha BETWEEN :desde AND :hasta ORDER BY h.fecha DESC, h.idHistorial DESC")
    Page<HistorialInventario> buscarAuditoria(@Param("idProducto") Integer idProducto,
                                              @Param("idBodega") Integer idBodega,
                                              @Param("desde") LocalDateTime desde,
                                              @Param("hasta") LocalDateTime hasta,
                                              Pageable pageable);

    @Query("SELECT COUNT(h) FROM HistorialInventario h WHERE h.inventario.producto.idProducto = :idProducto")
    Long contarMovimientosPorProducto(@Param("idProducto") Integer idProducto);

    @Query("SELECT MIN(h.stockNuevo) FROM HistorialInventario h WHERE h.inventario.producto.idProducto = :idProducto")
    Integer stockMinimoRegistrado(@Param("idProducto") Integer idProducto);

    @Query("SELECT MAX(h.stockNuevo) FROM HistorialInventario h WHERE h.inventario.producto.idProducto = :idProducto")
    Integer stockMaximoRegistrado(@Param("idProducto") Integer idProducto);

    @Query("SELECT MAX(h.fecha) FROM HistorialInventario h WHERE h.inventario.producto.idProducto = :idProducto")
    LocalDateTime ultimaActualizacion(@Param("idProducto") Integer idProducto);
}
