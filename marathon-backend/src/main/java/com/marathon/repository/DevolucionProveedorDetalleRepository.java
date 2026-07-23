package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.DevolucionProveedorDetalle;

public interface DevolucionProveedorDetalleRepository extends JpaRepository<DevolucionProveedorDetalle, Integer> {

    List<DevolucionProveedorDetalle> findByDevolucionProveedorIdDevolucionProv(Integer idDevolucionProv);

    boolean existsBySolicitudDevolucionDetalleIdDetalleSd(Integer idDetalleSd);

    boolean existsByRecepcionDetalleIdDetalleRm(Integer idDetalleRm);
}
