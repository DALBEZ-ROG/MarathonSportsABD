package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.marathon.model.Transportista;

public interface TransportistaRepository extends JpaRepository<Transportista, Integer> {

    /** Los que se pueden elegir hoy, por nombre. Es todo lo que necesita el empaque. */
    List<Transportista> findByEstadoOrderByNombreAsc(String estado);

    /**
     * Los activos <b>con su cobertura ya traída</b>, para el desplegable (F84).
     *
     * <p>El {@code JOIN FETCH} está aquí a propósito y no en el mapeo de la
     * entidad: la cobertura es LAZY porque en el resto del sistema —listados de
     * despachos, informes— no hace falta, y traerla siempre metía una consulta
     * por transportista distinto de la página. Aquí sí hace falta, y se pide.
     */
    @Query("SELECT DISTINCT t FROM Transportista t LEFT JOIN FETCH t.regiones "
         + "WHERE t.estado = 'activo' ORDER BY t.nombre")
    List<Transportista> activosConCobertura();
}
