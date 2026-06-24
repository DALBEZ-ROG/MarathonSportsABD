package com.marathon.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.marathon.dto.dashboard.MovimientoResumenDTO;
import com.marathon.model.MovimientoInventario;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Integer> {

    Page<MovimientoInventario> findByInventarioProductoIdProductoAndInventarioBodegaIdBodega(
            Integer idProducto, Integer idBodega, Pageable pageable);

    Page<MovimientoInventario> findByTipoMovimiento(String tipoMovimiento, Pageable pageable);

    @Query("SELECT new com.marathon.dto.dashboard.MovimientoResumenDTO(m.tipoMovimiento, COUNT(m), "
            + "COALESCE(SUM(m.cantidad),0)) FROM MovimientoInventario m "
            + "WHERE CAST(m.fecha AS LocalDate) = CURRENT_DATE "
            + "GROUP BY m.tipoMovimiento")
    List<MovimientoResumenDTO> movimientosHoy();
}
