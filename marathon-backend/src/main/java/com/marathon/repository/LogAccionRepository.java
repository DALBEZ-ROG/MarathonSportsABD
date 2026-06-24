package com.marathon.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.LogAccion;

public interface LogAccionRepository extends JpaRepository<LogAccion, Integer> {

    Page<LogAccion> findByUsuarioIdUsuario(Integer idUsuario, Pageable pageable);

    Page<LogAccion> findByModulo(String modulo, Pageable pageable);

    Page<LogAccion> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    @Query("SELECT l FROM LogAccion l WHERE (:idUsuario = 0 OR l.usuario.idUsuario = :idUsuario) "
            + "AND (:modulo = '' OR l.modulo = :modulo) "
            + "AND l.fecha BETWEEN :desde AND :hasta ORDER BY l.fecha DESC")
    Page<LogAccion> buscar(@Param("idUsuario") Integer idUsuario,
                           @Param("modulo") String modulo,
                           @Param("desde") LocalDateTime desde,
                           @Param("hasta") LocalDateTime hasta,
                           Pageable pageable);

    @Query("SELECT DISTINCT l.modulo FROM LogAccion l ORDER BY l.modulo")
    List<String> findDistinctModulos();
}
