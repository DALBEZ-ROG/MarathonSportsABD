package com.marathon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marathon.model.RolPermiso;

public interface RolPermisoRepository extends JpaRepository<RolPermiso, Integer> {

    List<RolPermiso> findByRolIdRol(Integer idRol);

    /**
     * Los permisos de una persona, ya en forma de {@code modulo:accion} (F94).
     *
     * <p><b>Esta consulta la paga CADA peticion del sistema.</b> El filtro JWT
     * resuelve el token consultando la base en cada llamada —a proposito desde
     * la F48, para que un cambio en la pantalla de roles surta efecto sin volver
     * a entrar— y para eso necesita la lista de permisos.
     *
     * <p>Antes se hacia recorriendo {@code findByRolIdRol} y pidiendo
     * {@code rp.getPermiso()} de cada fila. {@code RolPermiso.permiso} es EAGER,
     * pero EAGER sin {@code JOIN FETCH} no junta nada: Hibernate lanza un SELECT
     * por fila. Medido con la cuenta de administrador, que tiene 99 permisos:
     * <b>99 consultas a `permiso` en una sola peticion</b>, mas la del usuario y
     * la de los roles. Y eso multiplicaba TODAS las pantallas por igual.
     *
     * <p>Devolver las cadenas ya montadas evita ademas traer entidades que solo
     * se usaban para concatenar dos columnas.
     */
    @Query("SELECT DISTINCT concat(p.modulo, ':', p.accion) "
         + "FROM UsuarioRol ur JOIN ur.rol r, RolPermiso rp JOIN rp.permiso p "
         + "WHERE rp.rol = r AND ur.usuario.idUsuario = :idUsuario")
    List<String> permisosDeUsuario(@Param("idUsuario") Integer idUsuario);
}
