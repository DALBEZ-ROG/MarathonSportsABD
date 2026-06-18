package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.MovimientoInventario;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Integer> {

    Page<MovimientoInventario> findByInventarioProductoIdProductoAndInventarioBodegaIdBodega(
            Integer idProducto, Integer idBodega, Pageable pageable);

    Page<MovimientoInventario> findByTipoMovimiento(String tipoMovimiento, Pageable pageable);
}
