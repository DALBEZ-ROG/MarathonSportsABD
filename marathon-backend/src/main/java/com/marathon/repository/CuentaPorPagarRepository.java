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
         // También EXISTS. Nombrar `c.facturaCompra.ordenCompra.proveedor` aquí
         // encadenaba los tres joins en el FROM aunque el filtro viniera vacío,
         // que es como se abre la pantalla: 845 ms solo el count. Convertir el
         // filtro de texto no bastó porque este seguía arrastrándolos.
         + "AND (:idProveedor IS NULL OR EXISTS ("
         + "     SELECT 1 FROM FacturaCompra f2 WHERE f2 = c.facturaCompra "
         + "       AND f2.ordenCompra.proveedor.idProveedor = :idProveedor)) "
         + "AND (:numero IS NULL OR c.idCuentaPagar = :numero) "
         // F94 — EXISTS. Era la consulta más cara del sistema: nombrar
         // `c.facturaCompra.ordenCompra.proveedor.nombre` encadena TRES joins
         // (factura, orden, proveedor) en el FROM, y se pagaban siempre, también
         // al abrir la pantalla sin buscar nada. Medido, el count solo:
         //     con los tres joins ... 1.127 ms
         //     con EXISTS ...........    43 ms
         + "AND (:texto IS NULL OR EXISTS ("
         + "     SELECT 1 FROM FacturaCompra f WHERE f = c.facturaCompra AND ("
         + "         LOWER(f.numeroFacturaProveedor) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) "
         + "      OR EXISTS (SELECT 1 FROM Proveedor pr WHERE pr = f.ordenCompra.proveedor "
         + "                   AND LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%'))))))")
    Page<CuentaPorPagar> buscar(@Param("estado") String estado,
                                @Param("idProveedor") Integer idProveedor,
                                @Param("texto") String texto,
                                @Param("numero") Long numero,
                                Pageable pageable);
}
