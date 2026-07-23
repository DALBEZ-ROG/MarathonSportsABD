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

    Page<CuentaPorPagar> findByProveedorIdProveedor(Integer idProveedor, Pageable pageable);

    Page<CuentaPorPagar> findByEstadoAndProveedorIdProveedor(String estado, Integer idProveedor, Pageable pageable);

    Page<CuentaPorPagar> findByFechaVencimientoLessThanAndEstado(LocalDate fecha, String estado, Pageable pageable);

    Optional<CuentaPorPagar> findByFacturaCompraIdFacturaCompra(Integer idFacturaCompra);

    @Modifying
    @Query("UPDATE CuentaPorPagar c SET c.estado = 'vencida' WHERE c.estado = 'vigente' AND c.fechaVencimiento < :hoy")
    int actualizarVencidas(@Param("hoy") LocalDate hoy);

    @Query("SELECT COALESCE(SUM(c.saldoPendiente), 0) FROM CuentaPorPagar c WHERE c.proveedor.idProveedor = :idProveedor AND c.estado IN ('vigente','vencida')")
    BigDecimal totalAdeudadoPorProveedor(@Param("idProveedor") Integer idProveedor);

    @Query("SELECT COALESCE(SUM(c.saldoPendiente), 0) FROM CuentaPorPagar c WHERE c.estado IN ('vigente','vencida')")
    BigDecimal totalAdeudadoGlobal();

    long countByEstado(String estado);

    List<CuentaPorPagar> findByProveedorIdProveedorAndEstadoIn(Integer idProveedor, List<String> estados);
}
