package com.marathon.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.Inventario;

import jakarta.persistence.LockModeType;

public interface InventarioRepository extends JpaRepository<Inventario, Integer> {

    Page<Inventario> findByBodegaIdBodega(Integer idBodega, Pageable pageable);

    /**
     * Inventario con filtro de bodega y BUSQUEDA POR PRODUCTO (F54).
     *
     * <p>La pantalla solo tenia un desplegable de bodegas: para saber cuanto
     * queda de un articulo concreto habia que recorrer las paginas a mano.
     * Se busca por nombre de producto y por nombre de bodega.
     */
    // F94 — EXISTS, para que producto y bodega no entren en el FROM cuando no se
    // está buscando por texto. Ver la nota larga en PedidoRepository.buscar.
    // F94 — DOS consultas. Ver la nota larga en OrdenCompraRepository.
    @Query("SELECT i FROM Inventario i WHERE "
         + "(:idBodega IS NULL OR i.bodega.idBodega = :idBodega)")
    Page<Inventario> buscarSinTexto(@Param("idBodega") Integer idBodega, Pageable pageable);

    // =====================================================================
    // F94d — por PALABRAS, y solo sobre el producto
    // =====================================================================
    // Dos cambios, y el segundo es el que importa para el tiempo.
    //
    // 1) POR PALABRAS. «zapatilla nike» no encontraba nada, porque comparaba la
    //    frase entera contra un nombre que nunca la contiene seguida. Ahora cada
    //    palabra tiene que aparecer, en cualquier orden.
    //
    // 2) YA NO BUSCA POR NOMBRE DE BODEGA. El `OR bodega.nombre` impedía usar el
    //    índice: una condición que mira DOS tablas no se puede resolver con el
    //    índice de ninguna, así que PostgreSQL unía inventario con producto
    //    entero —1,5 millones de filas— y filtraba después. Se ve en el plan:
    //    «Hash Join / Rows Removed by Join Filter: 500000».
    //
    //        con  OR bodega ... 2.598 ms
    //        sin  OR bodega ...    56 ms
    //
    //    Y no se pierde nada: filtrar por bodega ya lo hace el desplegable que
    //    está justo al lado de la caja de texto, que además es exacto en vez de
    //    por coincidencia parcial.
    @Query("SELECT i FROM Inventario i JOIN i.producto pr WHERE "
         + "(:idBodega IS NULL OR i.bodega.idBodega = :idBodega) "
         + "AND LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:p1 AS string), '%')) "
         + "AND (:p2 IS NULL OR LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:p2 AS string), '%'))) "
         + "AND (:p3 IS NULL OR LOWER(pr.nombre) LIKE LOWER(CONCAT('%', CAST(:p3 AS string), '%')))")
    Page<Inventario> buscarConTexto(@Param("idBodega") Integer idBodega,
                                    @Param("p1") String p1,
                                    @Param("p2") String p2,
                                    @Param("p3") String p3,
                                    Pageable pageable);

    List<Inventario> findByProductoIdProducto(Integer idProducto);

    Optional<Inventario> findByProductoIdProductoAndBodegaIdBodega(Integer idProducto, Integer idBodega);

    // ------------------------------------------------------------------
    // Lecturas para MODIFICAR el stock (L1)
    // ------------------------------------------------------------------
    // Los dos metodos de abajo son los unicos que deben usarse cuando a
    // continuacion se va a escribir stock_actual. Emiten SELECT ... FOR UPDATE,
    // de modo que una segunda transaccion que quiera tocar la misma fila espera
    // en vez de leer un saldo que esta a punto de quedar obsoleto.
    //
    // Sin ese bloqueo, el patron leer-calcular-escribir que usan EmpaqueService,
    // InventarioService y SolicitudDevolucionService pierde actualizaciones: dos
    // salidas simultaneas de 5 sobre un stock de 10 leen ambas 10, calculan
    // ambas 5, y el saldo final queda en 5 en vez de 0.

    /**
     * Todas las filas de inventario de un producto, bloqueadas y en orden
     * estable por bodega. El orden importa: dos transacciones que bloqueen el
     * mismo conjunto de filas en el mismo orden no pueden abrazarse.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventario i WHERE i.producto.idProducto = :idProducto "
         + "ORDER BY i.bodega.idBodega")
    List<Inventario> buscarPorProductoParaActualizar(@Param("idProducto") Integer idProducto);

    /** La fila de inventario de un producto en una bodega concreta, bloqueada. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventario i WHERE i.producto.idProducto = :idProducto "
         + "AND i.bodega.idBodega = :idBodega")
    Optional<Inventario> buscarParaActualizar(@Param("idProducto") Integer idProducto,
                                              @Param("idBodega") Integer idBodega);

    /**
     * Bajo minimo segun el minimo de cada fila, que es la definicion que usa el
     * tablero (D2) y la que respondia ya {@link #contarProductosStockBajo()}.
     *
     * <p>Sustituye a un {@code findStockBajo(int umbral)} al que la pantalla de
     * Inventario llamaba siempre con un 5: un umbral fijo, igual para una
     * referencia que rota cinco unidades al mes que para una que rota
     * quinientas. Con eso, la misma pregunta —«¿cuantas referencias hay que
     * reponer?»— tenia dos respuestas segun donde se mirara: 116 en Inventario
     * y 220 en el tablero.
     *
     * <h3>F94 — {@code JOIN FETCH} y acotada</h3>
     *
     * <p>Dos problemas, medidos: <b>9,5 segundos</b> por carga.
     *
     * <p>El primero, que devolvía las 50.153 filas bajo mínimos de golpe. La
     * pantalla que la consume solo usa el <i>número</i> para pintar un aviso, y
     * quien mira la lista va a reponer — nadie repone cincuenta mil referencias
     * de una sentada. El total exacto lo da {@link #contarProductosStockBajo()}.
     *
     * <p>El segundo, el {@code ORDER BY i.producto.nombre} sin traerse el
     * producto: cada fila del resultado pedía después su producto y su bodega
     * una a una al construir el DTO. {@code JOIN FETCH} los trae en la misma
     * consulta.
     */
    @Query("SELECT i FROM Inventario i "
         + "JOIN FETCH i.producto p JOIN FETCH i.bodega b "
         + "WHERE i.stockActual <= i.stockMinimo AND i.stockMinimo > 0 "
         + "ORDER BY p.nombre ASC, b.nombre ASC")
    List<Inventario> findBajoMinimo(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT i FROM Inventario i WHERE i.bodega.idBodega = :idBodega AND i.stockActual <= :umbral")
    List<Inventario> findStockBajoByBodega(@Param("idBodega") Integer idBodega, @Param("umbral") int umbral);

    /**
     * Existencias totales de un producto sumando todas las bodegas.
     *
     * <p>Es el numerador del disponible que usa la reserva (F47, D-02):
     * disponible = stockTotalDe(p) - reservas activas de p. Se suma en la base
     * y no en memoria porque quien pregunta esto —crear un pedido, procesarlo—
     * no necesita las filas, solo el total.
     */
    @Query("SELECT COALESCE(SUM(i.stockActual), 0) FROM Inventario i WHERE i.producto.idProducto = :idProducto")
    int stockTotalDe(@Param("idProducto") Integer idProducto);

    @Query("SELECT COUNT(i) FROM Inventario i WHERE i.stockActual <= i.stockMinimo AND i.stockMinimo > 0")
    Long contarProductosStockBajo();
}
