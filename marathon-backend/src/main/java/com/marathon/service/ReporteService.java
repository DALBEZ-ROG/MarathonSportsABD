package com.marathon.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.marathon.dto.reporte.FiltroReporteDTO;
import com.marathon.dto.reporte.ReporteMovimientosItemDTO;
import com.marathon.dto.reporte.ReportePedidosItemDTO;
import com.marathon.dto.reporte.ReporteVentasProductoItemDTO;
import com.marathon.model.MovimientoInventario;
import com.marathon.model.Pedido;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

@Service
public class ReporteService {

    @PersistenceContext
    private EntityManager entityManager;

    public List<ReportePedidosItemDTO> generarReportePedidos(FiltroReporteDTO f) {
        StringBuilder jpql = new StringBuilder("SELECT p FROM Pedido p WHERE 1=1");
        if (f.getDesde() != null) {
            jpql.append(" AND p.fechaPedido >= :desde");
        }
        if (f.getHasta() != null) {
            jpql.append(" AND p.fechaPedido <= :hasta");
        }
        if (f.getEstado() != null && !f.getEstado().isBlank()) {
            jpql.append(" AND p.estado = :estado");
        }
        if (f.getRegionDestino() != null && !f.getRegionDestino().isBlank()) {
            jpql.append(" AND p.regionDestino = :region");
        }
        jpql.append(" ORDER BY p.fechaPedido DESC");

        TypedQuery<Pedido> query = entityManager.createQuery(jpql.toString(), Pedido.class);
        if (f.getDesde() != null) {
            query.setParameter("desde", f.getDesde());
        }
        if (f.getHasta() != null) {
            query.setParameter("hasta", f.getHasta());
        }
        if (f.getEstado() != null && !f.getEstado().isBlank()) {
            query.setParameter("estado", f.getEstado());
        }
        if (f.getRegionDestino() != null && !f.getRegionDestino().isBlank()) {
            query.setParameter("region", f.getRegionDestino());
        }
        query.setMaxResults(f.getLimiteEfectivo());

        List<ReportePedidosItemDTO> resultado = new ArrayList<>();
        for (Pedido p : query.getResultList()) {
            String cliente = null;
            String ciudad = null;
            if (p.getCliente() != null) {
                cliente = nz(p.getCliente().getNombre()) + " " + nz(p.getCliente().getApellido());
                cliente = cliente.trim();
                if (p.getCliente().getCiudad() != null) {
                    ciudad = p.getCliente().getCiudad().getNombre();
                }
            }
            resultado.add(new ReportePedidosItemDTO(
                    p.getIdPedido(),
                    p.getFechaPedido(),
                    p.getEstado(),
                    cliente,
                    ciudad,
                    p.getRegionDestino(),
                    p.getTransportista(),
                    p.getTotal(),
                    p.getDescuento(),
                    p.getEsPedidoEspecial(),
                    p.getTipoEspecial()));
        }
        return resultado;
    }

    public List<ReporteVentasProductoItemDTO> generarReporteVentasProducto(FiltroReporteDTO f) {
        StringBuilder jpql = new StringBuilder(
                "SELECT d.producto.idProducto, d.producto.nombre, d.producto.categoria.nombre, "
                + "d.producto.unidadMedida.nombre, SUM(d.cantidad), SUM(d.subtotal), "
                + "COUNT(DISTINCT d.pedido.idPedido) "
                + "FROM DetallePedido d WHERE d.pedido.estado = 'entregado'");
        if (f.getDesde() != null) {
            jpql.append(" AND d.pedido.fechaPedido >= :desde");
        }
        if (f.getHasta() != null) {
            jpql.append(" AND d.pedido.fechaPedido <= :hasta");
        }
        if (f.getIdCategoria() != null) {
            jpql.append(" AND d.producto.categoria.idCategoria = :idCategoria");
        }
        jpql.append(" GROUP BY d.producto.idProducto, d.producto.nombre, d.producto.categoria.nombre, "
                + "d.producto.unidadMedida.nombre ORDER BY SUM(d.subtotal) DESC");

        TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class);
        if (f.getDesde() != null) {
            query.setParameter("desde", f.getDesde());
        }
        if (f.getHasta() != null) {
            query.setParameter("hasta", f.getHasta());
        }
        if (f.getIdCategoria() != null) {
            query.setParameter("idCategoria", f.getIdCategoria());
        }
        query.setMaxResults(f.getLimiteEfectivo());

        List<ReporteVentasProductoItemDTO> resultado = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            Integer idProducto = (Integer) row[0];
            String nombreProducto = (String) row[1];
            String categoria = (String) row[2];
            String unidadMedida = (String) row[3];
            Long cantidadVendida = ((Number) row[4]).longValue();
            BigDecimal totalIngresos = row[5] != null ? (BigDecimal) row[5] : BigDecimal.ZERO;
            Long numeroPedidos = ((Number) row[6]).longValue();

            BigDecimal precioPromedio = BigDecimal.ZERO;
            if (cantidadVendida != null && cantidadVendida > 0) {
                precioPromedio = totalIngresos.divide(
                        BigDecimal.valueOf(cantidadVendida), 2, RoundingMode.HALF_UP);
            }

            resultado.add(new ReporteVentasProductoItemDTO(
                    idProducto, nombreProducto, categoria, unidadMedida,
                    cantidadVendida, totalIngresos, precioPromedio, numeroPedidos));
        }
        return resultado;
    }

    public List<ReporteMovimientosItemDTO> generarReporteMovimientos(FiltroReporteDTO f) {
        StringBuilder jpql = new StringBuilder("SELECT m FROM MovimientoInventario m WHERE 1=1");
        if (f.getDesde() != null) {
            jpql.append(" AND m.fecha >= :desde");
        }
        if (f.getHasta() != null) {
            jpql.append(" AND m.fecha <= :hasta");
        }
        if (f.getIdBodega() != null) {
            jpql.append(" AND m.inventario.bodega.idBodega = :idBodega");
        }
        if (f.getEstado() != null && !f.getEstado().isBlank()) {
            jpql.append(" AND m.tipoMovimiento = :tipo");
        }
        jpql.append(" ORDER BY m.fecha DESC");

        TypedQuery<MovimientoInventario> query = entityManager.createQuery(jpql.toString(), MovimientoInventario.class);
        if (f.getDesde() != null) {
            query.setParameter("desde", f.getDesde());
        }
        if (f.getHasta() != null) {
            query.setParameter("hasta", f.getHasta());
        }
        if (f.getIdBodega() != null) {
            query.setParameter("idBodega", f.getIdBodega());
        }
        if (f.getEstado() != null && !f.getEstado().isBlank()) {
            query.setParameter("tipo", f.getEstado());
        }
        query.setMaxResults(f.getLimiteEfectivo());

        List<ReporteMovimientosItemDTO> resultado = new ArrayList<>();
        for (MovimientoInventario m : query.getResultList()) {
            String producto = null;
            String bodega = null;
            if (m.getInventario() != null) {
                if (m.getInventario().getProducto() != null) {
                    producto = m.getInventario().getProducto().getNombre();
                }
                if (m.getInventario().getBodega() != null) {
                    bodega = m.getInventario().getBodega().getNombre();
                }
            }
            String bodegaDestino = null;
            if (m.getInventarioDestino() != null && m.getInventarioDestino().getBodega() != null) {
                bodegaDestino = m.getInventarioDestino().getBodega().getNombre();
            }
            String usuario = null;
            if (m.getUsuario() != null) {
                usuario = (nz(m.getUsuario().getNombre()) + " " + nz(m.getUsuario().getApellido())).trim();
            }
            resultado.add(new ReporteMovimientosItemDTO(
                    m.getIdMovimiento(),
                    m.getTipoMovimiento(),
                    m.getCantidad(),
                    m.getFecha(),
                    m.getObservacion(),
                    producto,
                    bodega,
                    bodegaDestino,
                    usuario));
        }
        return resultado;
    }

    private String nz(String s) {
        return s != null ? s : "";
    }
}
