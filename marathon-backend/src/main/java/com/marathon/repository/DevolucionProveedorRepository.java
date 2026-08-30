package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.DevolucionProveedor;

public interface DevolucionProveedorRepository extends JpaRepository<DevolucionProveedor, Integer> {

    Page<DevolucionProveedor> findByEstado(String estado, Pageable pageable);

    Page<DevolucionProveedor> findByProveedorIdProveedor(Integer idProveedor, Pageable pageable);

    Page<DevolucionProveedor> findByEstadoAndProveedorIdProveedor(String estado, Integer idProveedor, Pageable pageable);

    /** Listado con filtros y busqueda por numero de devolucion o proveedor (F54). */
    @Query("SELECT d FROM DevolucionProveedor d WHERE "
         + "(:estado IS NULL OR d.estado = :estado) "
         + "AND (:idProveedor IS NULL OR d.proveedor.idProveedor = :idProveedor) "
         + "AND (:numero IS NULL OR d.idDevolucionProv = :numero) "
         + "AND (:texto IS NULL OR EXISTS ("
         + "     SELECT 1 FROM Proveedor pr WHERE pr = d.proveedor"
         + "       AND LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%'))))")
    Page<DevolucionProveedor> buscar(@Param("estado") String estado,
                                     @Param("idProveedor") Integer idProveedor,
                                     @Param("texto") String texto,
                                     @Param("numero") Long numero,
                                     Pageable pageable);
}
