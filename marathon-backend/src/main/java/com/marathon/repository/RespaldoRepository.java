package com.marathon.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.marathon.model.Respaldo;

public interface RespaldoRepository extends JpaRepository<Respaldo, Long> {

    List<Respaldo> findAllByOrderByFechaInicioDesc();

    List<Respaldo> findByEstadoOrderByFechaInicioDesc(String estado);

    /**
     * El ultimo respaldo que termino bien.
     *
     * <p>Sirve para dos cosas: decir en la pantalla cuanto hace que no se
     * respalda, y estimar el tamano del proximo volcado para poder dibujar una
     * barra de progreso que signifique algo.
     */
    @Query("SELECT r FROM Respaldo r WHERE r.estado = 'COMPLETADO' ORDER BY r.fechaInicio DESC LIMIT 1")
    Optional<Respaldo> ultimoCompletado();
}
