package com.marathon.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    Page<Cliente> findByEstado(String estado, Pageable pageable);

    /**
     * Busca por documento para poder avisar del duplicado ANTES de chocar con
     * el indice unico (F73).
     *
     * <p>El indice {@code uq_cliente_documento} ya impide repetirlo, pero su
     * violacion llega como un conflicto generico —"puede que el registro ya
     * exista"— que no dice ni cual es el dato ni de quien es. Esto no sustituye
     * al indice: lo precede, para dar un mensaje que se entienda.
     */
    Optional<Cliente> findByNumeroDocumento(String numeroDocumento);

    @Query("SELECT c FROM Cliente c WHERE (LOWER(c.nombre) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(c.apellido) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<Cliente> findByNombreOrApellido(@Param("search") String search, Pageable pageable);

    @Query("SELECT c FROM Cliente c WHERE (LOWER(c.nombre) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(c.apellido) LIKE LOWER(CONCAT('%',:search,'%'))) AND c.estado = :estado")
    Page<Cliente> findByNombreOrApellidoAndEstado(@Param("search") String search, @Param("estado") String estado, Pageable pageable);

    List<Cliente> findByEstadoOrderByApellidoAsc(String estado);

    /**
     * Clientes activos SIN los datos de contacto cifrados, para el selector de
     * "Pedido nuevo".
     *
     * <p><b>Por que una proyeccion y no la entidad.</b> Desde la F41,
     * {@code Cliente.correo}, {@code telefono} y {@code direccion} son campos
     * {@code @Formula} que llaman a {@code fn_descifrar()}, asi que cargar la
     * entidad descifra TRES campos POR FILA. Medido sobre las 4.620 filas
     * activas: 2,4 ms sin descifrar frente a <b>4.904 ms descifrando</b>, y el
     * endpoint completo pasaba de milisegundos a <b>6 segundos</b>.
     *
     * <p>Y el descifrado ahi no servia para nada: el selector solo pinta
     * "nombre apellido". Se estaban descifrando 13.860 datos
     * personales para no mostrar ninguno. Seleccionar solo las columnas que se
     * usan es a la vez lo rapido y lo correcto en proteccion de datos.
     *
     * <p>Devuelve {@code Object[]}: idCliente, nombre, apellido, estado,
     * idCiudad, nombreCiudad.
     */
    @Query("SELECT c.idCliente, c.nombre, c.apellido, c.estado, ci.idCiudad, ci.nombre "
         + "FROM Cliente c JOIN c.ciudad ci WHERE c.estado = :estado ORDER BY c.apellido ASC")
    List<Object[]> listarActivosSinContacto(@Param("estado") String estado);

    // El buscador de «Pedido nuevo» (F93) NO esta aqui: vive en
    // ClienteService.buscarParaSelector, con EntityManager. Es SQL nativo, y
    // declararlo como @Query(nativeQuery = true) hace que Spring Data se lo pase
    // a JSqlParser para analizarlo — y en este proyecto esa combinacion revienta
    // el arranque con un NoSuchMethodError entre spring-data-jpa 3.2.2 y
    // jsqlparser 4.9. Es la misma razon por la que LogService y
    // AuditoriaCambiosService usan EntityManager para su SQL nativo.
}
