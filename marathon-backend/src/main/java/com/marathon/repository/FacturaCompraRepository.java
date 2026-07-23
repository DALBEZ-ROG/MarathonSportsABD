package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.FacturaCompra;

public interface FacturaCompraRepository extends JpaRepository<FacturaCompra, Integer> {

    Page<FacturaCompra> findByEstado(String estado, Pageable pageable);

    Page<FacturaCompra> findByOrdenCompraProveedorIdProveedor(Integer idProveedor, Pageable pageable);

    Page<FacturaCompra> findByEstadoAndOrdenCompraProveedorIdProveedor(String estado, Integer idProveedor, Pageable pageable);

    boolean existsByOrdenCompraIdOrdenCompraAndNumeroFacturaProveedor(Integer idOrdenCompra, String numeroFacturaProveedor);
}
