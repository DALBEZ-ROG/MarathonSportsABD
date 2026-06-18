package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.HistorialInventario;

public interface HistorialInventarioRepository extends JpaRepository<HistorialInventario, Integer> {

    List<HistorialInventario> findByInventarioIdInventarioOrderByFechaCambioDesc(Integer idInventario);
}
