package com.marathon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.DevolucionProveedor;

public interface DevolucionProveedorRepository extends JpaRepository<DevolucionProveedor, Integer> {

    Page<DevolucionProveedor> findByEstado(String estado, Pageable pageable);

    Page<DevolucionProveedor> findByProveedorIdProveedor(Integer idProveedor, Pageable pageable);

    Page<DevolucionProveedor> findByEstadoAndProveedorIdProveedor(String estado, Integer idProveedor, Pageable pageable);
}
