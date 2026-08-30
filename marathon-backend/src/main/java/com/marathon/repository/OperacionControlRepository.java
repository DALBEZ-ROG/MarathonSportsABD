package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marathon.model.OperacionControl;

public interface OperacionControlRepository extends JpaRepository<OperacionControl, Long> {

    List<OperacionControl> findAllByOrderByFechaInicioDesc();
}
