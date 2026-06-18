package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.ProductoProveedor;

public interface ProductoProveedorRepository extends JpaRepository<ProductoProveedor, Integer> {

    List<ProductoProveedor> findByProductoIdProducto(Integer idProducto);

    void deleteByProductoIdProducto(Integer idProducto);
}
