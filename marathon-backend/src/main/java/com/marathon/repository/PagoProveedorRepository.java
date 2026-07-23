package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.PagoProveedor;

public interface PagoProveedorRepository extends JpaRepository<PagoProveedor, Integer> {

    List<PagoProveedor> findByCuentaPorPagarIdCuentaPagarOrderByFechaPagoDesc(Integer idCuentaPagar);
}
