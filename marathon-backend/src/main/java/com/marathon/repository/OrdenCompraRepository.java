package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.OrdenCompra;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Integer> {

    Page<OrdenCompra> findByEstado(String estado, Pageable pageable);

    Page<OrdenCompra> findByProveedorIdProveedor(Integer idProveedor, Pageable pageable);

    Page<OrdenCompra> findByEstadoAndProveedorIdProveedor(String estado, Integer idProveedor, Pageable pageable);

    long countByEstado(String estado);
}
