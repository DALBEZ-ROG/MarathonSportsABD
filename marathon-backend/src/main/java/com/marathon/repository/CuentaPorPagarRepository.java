package com.marathon.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.CuentaPorPagar;

public interface CuentaPorPagarRepository extends JpaRepository<CuentaPorPagar, Integer> {

    Page<CuentaPorPagar> findByEstado(String estado, Pageable pageable);

    // F84: aqui habia findByProveedorIdProveedor y findByEstadoAndProveedorIdProveedor.
    // Se van por dos motivos a la vez: nadie las llamaba, y desde la F84 el
    // proveedor ya no es un campo de la cuenta —se llega a el por la factura—,
    // asi que Spring Data ni siquiera podria derivarlas del nombre. Lo que si se
    // usa esta abajo, escrito con el camino entero.

    Page<CuentaPorPagar> findByFechaVencimientoLessThanAndEstado(LocalDate fecha, String estado, Pageable pageable);

    Optional<CuentaPorPagar> findByFacturaCompraIdFacturaCompra(Integer idFacturaCompra);

    @Modifying
    @Query("UPDATE CuentaPorPagar c SET c.estado = 'vencida' WHERE c.estado = 'vigente' AND c.fechaVencimiento < :hoy")
    int actualizarVencidas(@Param("hoy") LocalDate hoy);

    @Query("SELECT COALESCE(SUM(c.saldoPendiente), 0) FROM CuentaPorPagar c "
         + "WHERE c.facturaCompra.ordenCompra.proveedor.idProveedor = :idProveedor "
         + "AND c.estado IN ('vigente','vencida')")
    BigDecimal totalAdeudadoPorProveedor(@Param("idProveedor") Integer idProveedor);

    @Query("SELECT COALESCE(SUM(c.saldoPendiente), 0) FROM CuentaPorPagar c WHERE c.estado IN ('vigente','vencida')")
    BigDecimal totalAdeudadoGlobal();

    long countByEstado(String estado);

    /** Las cuentas vivas de un proveedor. F84: se llega por la factura y su orden. */
    @Query("SELECT c FROM CuentaPorPagar c "
         + "WHERE c.facturaCompra.ordenCompra.proveedor.idProveedor = :idProveedor "
         + "AND c.estado IN :estados")
    List<CuentaPorPagar> deProveedorEnEstados(@Param("idProveedor") Integer idProveedor,
                                              @Param("estados") List<String> estados);

    /** Listado con filtros y busqueda por proveedor o numero de factura (F54). */
    @Query("SELECT c FROM CuentaPorPagar c WHERE "
         + "(:estado IS NULL OR c.estado = :estado) "
         + "AND (:idProveedor IS NULL OR c.facturaCompra.ordenCompra.proveedor.idProveedor = :idProveedor) "
         + "AND (:texto IS NULL "
         + "     OR CAST(c.idCuentaPagar AS string) LIKE CONCAT('%', CAST(:texto AS string), '%') "
         + "     OR LOWER(c.facturaCompra.ordenCompra.proveedor.nombre) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) "
         + "     OR LOWER(c.facturaCompra.numeroFacturaProveedor) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')))")
    Page<CuentaPorPagar> buscar(@Param("estado") String estado,
                                @Param("idProveedor") Integer idProveedor,
                                @Param("texto") String texto,
                                Pageable pageable);
}
