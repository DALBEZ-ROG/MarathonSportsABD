package com.marathon.dto.ciudad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lo que el formulario de ciudad manda.
 *
 * <p><b>F85 — llega la región, que faltaba.</b> La columna {@code ciudad.region}
 * existe desde la F77 y desde la F84 es el <b>único</b> sitio de donde sale la
 * región de destino de un envío. El formulario no la pedía, así que toda ciudad
 * creada desde la pantalla nacía con la región vacía: sus pedidos no enseñaban
 * destino y no aparecían nunca en el filtro de despachos por región. No fallaba
 * nada; simplemente no salía.
 *
 * <p>Es opcional, no obligatoria, porque las 88 ciudades que ya había se
 * clasificaron en la F77 y una ciudad sin clasificar tiene que poder existir —lo
 * que no puede es fingir una región—. Ver {@code DestinoDelEmpaqueTest}.
 */
public class CiudadRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String nombre;

    /** Las cuatro del CHECK {@code chk_ciudad_region}. Ni una más. */
    @Pattern(regexp = "Costa|Sierra|Oriente|Insular",
             message = "La región debe ser Costa, Sierra, Oriente o Insular")
    private String region;

    @Pattern(regexp = "activo|inactivo", message = "El estado debe ser 'activo' o 'inactivo'")
    private String estado;

    public CiudadRequestDTO() {}

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
