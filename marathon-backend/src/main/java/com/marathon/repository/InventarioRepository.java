package com.marathon.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.Inventario;

public interface InventarioRepository extends JpaRepository<Inventario, Integer> {

    Page<Inventario> findByBodegaIdBodega(Integer idBodega, Pageable pageable);

    List<Inventario> findByProductoIdProducto(Integer idProducto);

    Optional<Inventario> findByProductoIdProductoAndBodegaIdBodega(Integer idProducto, Integer idBodega);

    @Query("SELECT i FROM Inventario i WHERE i.stockActual <= :umbral")
    List<Inventario> findStockBajo(@Param("umbral") int umbral);

    @Query("SELECT i FROM Inventario i WHERE i.bodega.idBodega = :idBodega AND i.stockActual <= :umbral")
    List<Inventario> findStockBajoByBodega(@Param("idBodega") Integer idBodega, @Param("umbral") int umbral);
}
