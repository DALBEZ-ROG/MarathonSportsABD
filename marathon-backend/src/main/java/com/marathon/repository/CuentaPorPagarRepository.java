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

    /**
     * Cuántas cuentas vencidas hay y cuánto suman, LAS DOS COSAS EN LA BASE (F94c).
     *
     * <p><b>El aviso de la pantalla mentía.</b> Pedía
     * {@code ?estado=vencida&size=1000} y sumaba en el navegador lo que llegara,
     * pero enseñaba el {@code totalElements} del servidor como número de
     * cuentas. Con 1.499.192 cuentas vencidas, el resultado era:
     *
     * <pre>
     *   «1499192 cuenta(s) vencida(s) por un total de $43.950.118,75»
     *   total real ................................ $46.124.820.094,14
     * </pre>
     *
     * <p>El recuento correcto junto a la suma de las primeras mil filas, con
     * aspecto de dato bueno. Subestimaba la deuda por un factor de mil, y con
     * pocos datos no se notaba porque las mil filas eran todas.
     *
     * <p>Sumar es trabajo de la base. Devuelve {@code [cuantas, total]}.
     */
    @Query("SELECT COUNT(c), COALESCE(SUM(c.saldoPendiente), 0) "
         + "FROM CuentaPorPagar c WHERE c.estado = 'vencida'")
    Object[] resumenVencidas();

    @Query("SELECT COALESCE(SUM(c.saldoPendiente), 0) FROM CuentaPorPagar c WHERE c.estado IN ('vigente','vencida')")
    BigDecimal totalAdeudadoGlobal();

    long countByEstado(String estado);

    /** Las cuentas vivas de un proveedor. F84: se llega por la factura y su orden. */
    @Query("SELECT c FROM CuentaPorPagar c "
         + "WHERE c.facturaCompra.ordenCompra.proveedor.idProveedor = :idProveedor "
         + "AND c.estado IN :estados")
    List<CuentaPorPagar> deProveedorEnEstados(@Param("idProveedor") Integer idProveedor,
                                              @Param("estados") List<String> estados);

    // =====================================================================
    // F94 — DOS consultas. Esta era la más cara del sistema.
    // =====================================================================
    // El proveedor de una cuenta por pagar está a tres saltos:
    // cuenta -> factura -> orden -> proveedor. Nombrarlo en el WHERE encadena
    // los tres joins en el FROM y se pagan SIEMPRE, también al abrir la
    // pantalla sin buscar nada: 1.127 ms solo el recuento de la paginación.
    // Ver la nota larga en OrdenCompraRepository sobre por qué no basta con
    // convertirlos en EXISTS.

    /** Sin texto: no se nombra la factura ni el proveedor, así que no hay uniones. */
    @Query("SELECT c FROM CuentaPorPagar c WHERE "
         + "(:estado IS NULL OR c.estado = :estado) "
         + "AND (:numero IS NULL OR c.idCuentaPagar = :numero)")
    Page<CuentaPorPagar> buscarSinTexto(@Param("estado") String estado,
                                        @Param("numero") Long numero,
                                        Pageable pageable);

    /** Filtro por proveedor: las tres uniones hacen falta, y son explícitas. */
    @Query("SELECT c FROM CuentaPorPagar c "
         + "JOIN c.facturaCompra f JOIN f.ordenCompra o JOIN o.proveedor pr WHERE "
         + "(:estado IS NULL OR c.estado = :estado) "
         + "AND pr.idProveedor = :idProveedor")
    Page<CuentaPorPagar> buscarPorProveedor(@Param("estado") String estado,
                                            @Param("idProveedor") Integer idProveedor,
                                            Pageable pageable);

    /** Búsqueda por nombre de proveedor o número de factura del proveedor. */
    @Query("SELECT c FROM CuentaPorPagar c "
         + "JOIN c.facturaCompra f JOIN f.ordenCompra o JOIN o.proveedor pr WHERE "
         + "(:estado IS NULL OR c.estado = :estado) "
         + "AND (:idProveedor IS NULL OR pr.idProveedor = :idProveedor) "
         + "AND (LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')) "
         + "  OR LOWER(f.numeroFacturaProveedor) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')))")
    Page<CuentaPorPagar> buscarConTexto(@Param("estado") String estado,
                                        @Param("idProveedor") Integer idProveedor,
                                        @Param("texto") String texto,
                                        Pageable pageable);
}
