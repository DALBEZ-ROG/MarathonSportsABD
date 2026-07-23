package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.OrdenCompraDetalle;

public interface OrdenCompraDetalleRepository extends JpaRepository<OrdenCompraDetalle, Integer> {

    List<OrdenCompraDetalle> findByOrdenCompraIdOrdenCompra(Integer idOrdenCompra);
}
