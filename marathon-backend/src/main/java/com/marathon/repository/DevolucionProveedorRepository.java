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
    // F94 — DOS consultas: sin texto no se une nada; con texto la unión es
    // explícita. Una sola con la condición anulable da mal plan en ambos casos.
    // Ver la nota larga en OrdenCompraRepository.
    @Query("SELECT d FROM DevolucionProveedor d WHERE "
         + "(:estado IS NULL OR d.estado = :estado) "
         + "AND (:idProveedor IS NULL OR d.proveedor.idProveedor = :idProveedor) "
         + "AND (:numero IS NULL OR d.idDevolucionProv = :numero)")
    Page<DevolucionProveedor> buscarSinTexto(@Param("estado") String estado,
                                             @Param("idProveedor") Integer idProveedor,
                                             @Param("numero") Long numero,
                                             Pageable pageable);

    @Query("SELECT d FROM DevolucionProveedor d JOIN d.proveedor pr WHERE "
         + "(:estado IS NULL OR d.estado = :estado) "
         + "AND (:idProveedor IS NULL OR pr.idProveedor = :idProveedor) "
         // F94d — por palabras. Ver Filtros.palabras().
         + "AND LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:p1 AS string), '%')) "
         + "AND (:p2 IS NULL OR LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:p2 AS string), '%'))) "
         + "AND (:p3 IS NULL OR LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:p3 AS string), '%')))")
    Page<DevolucionProveedor> buscarConTexto(@Param("estado") String estado,
                                             @Param("idProveedor") Integer idProveedor,
                                             @Param("p1") String p1,
                                             @Param("p2") String p2,
                                             @Param("p3") String p3,
                                             Pageable pageable);
}
