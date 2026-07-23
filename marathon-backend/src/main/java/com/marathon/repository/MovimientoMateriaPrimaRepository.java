package com.marathon.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.MovimientoMateriaPrima;

public interface MovimientoMateriaPrimaRepository extends JpaRepository<MovimientoMateriaPrima, Integer> {

    Page<MovimientoMateriaPrima> findByMateriaPrimaIdMateriaPrima(Integer idMateriaPrima, Pageable pageable);

    List<MovimientoMateriaPrima> findByMateriaPrimaIdMateriaPrimaOrderByFechaDesc(Integer idMateriaPrima);
}
