package com.marathon.model;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Quien lleva el bulto (F77).
 *
 * <p><b>Antes esto no existía.</b> El transportista era un {@code VARCHAR(100)}
 * libre en {@code pedido.transportista}, escrito a mano en cada empaque. En
 * 19.000 pedidos había exactamente <b>un</b> valor —«Servientrega», de una
 * prueba—, y con texto libre «Servientrega», «servientrega» y «Servi entrega»
 * son tres transportistas distintos para cualquier consulta: no se puede
 * responder «cuánto mandamos por cada uno» sin adivinar.
 *
 * <p>Es un catálogo de <b>solo lectura</b> desde la aplicación, como ciudad o
 * unidad de medida. Dar de alta un transportista es una decisión de negocio, no
 * una casilla de la pantalla de almacén; por eso la F77 no concede INSERT ni
 * UPDATE a ningún rol.
 *
 * <p><b>F84 — la cobertura dejó de ser una frase.</b> Hasta la F84 esto era
 * {@code cobertura VARCHAR(100)} con textos como «Nacional, incluye Oriente» o
 * «Costa y Sierra»: una <i>lista metida dentro de una columna</i>, que incumple
 * la 1FN. La base no podía responder «¿quién llega al Oriente?» sin leer prosa,
 * y esa pregunta la hace el empaque cada vez, porque ya sabe a qué región va el
 * bulto. Ahora las regiones son filas ({@link #regiones}) y lo que una lista de
 * regiones no sabe decir —«flota propia», «solo Quito y Guayaquil»— se queda en
 * {@link #nota}, que es texto para leer y no dato para consultar.
 */
@Entity
@Table(name = "transportista")
public class Transportista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_transportista")
    private Integer idTransportista;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /**
     * Las regiones a las que llega.
     *
     * <p><b>LAZY, y no es un detalle.</b> Empezó siendo EAGER —«son siete filas,
     * qué más da»— y eso metió una consulta extra en el listado de despachos,
     * que se cazó porque hay una prueba que <i>cuenta consultas</i>
     * ({@code RendimientoDespachosTest}). El problema no era la consulta de más:
     * era que crecía con el número de transportistas distintos de la página,
     * o sea un N+1 esperando a que alguien usara más de un transportista.
     *
     * <p>La cobertura solo hace falta en un sitio —el catálogo que llena el
     * desplegable del empaque—, y allí se trae con {@code JOIN FETCH} explícito.
     * Ver {@code TransportistaRepository.activosConCobertura()}.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "transportista_cobertura",
                     joinColumns = @JoinColumn(name = "id_transportista"))
    @Column(name = "region", nullable = false, length = 20)
    private Set<String> regiones = new LinkedHashSet<>();

    /** El matiz que una lista de regiones no expresa. Puede ser nulo. */
    @Column(name = "nota", length = 150)
    private String nota;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    public Transportista() {}

    public Integer getIdTransportista() { return idTransportista; }
    public void setIdTransportista(Integer idTransportista) { this.idTransportista = idTransportista; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Set<String> getRegiones() { return regiones; }
    public void setRegiones(Set<String> regiones) { this.regiones = regiones; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    /** ¿Llega a esta región? Con la cobertura en prosa esto no se podía preguntar. */
    public boolean llegaA(String region) {
        return region != null && regiones != null && regiones.contains(region);
    }
}
