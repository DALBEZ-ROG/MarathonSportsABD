package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.OrdenProduccion;

public interface OrdenProduccionRepository extends JpaRepository<OrdenProduccion, Integer> {

    @Query("SELECT o FROM OrdenProduccion o WHERE "
        + "(:estado IS NULL OR o.estado = :estado) AND "
        + "(:idProducto IS NULL OR o.producto.idProducto = :idProducto)")
    Page<OrdenProduccion> buscar(@Param("estado") String estado,
                                 @Param("idProducto") Integer idProducto,
                                 Pageable pageable);

    long countByEstado(String estado);
}
