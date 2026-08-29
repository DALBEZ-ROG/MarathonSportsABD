package com.marathon.dto.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClienteRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    private String apellido;

    /**
     * {@code cedula}, {@code ruc} o {@code pasaporte} (F73).
     *
     * <p>Sustituye al antiguo campo {@code cedula}, que <b>no se guardaba en
     * ningún sitio</b>: la tabla no tenía columna para él, así que se pedía como
     * obligatorio y se tiraba.
     *
     * <p><b>Los dos son opcionales</b>, y no es un olvido: los 5.000 clientes
     * que ya existen no tienen documento, y exigirlo aquí impediría editarles el
     * teléfono. Lo que sí se exige, en {@code ClienteService}, es que si se pone
     * esté completo y bien formado.
     */
    private String tipoDocumento;

    @Size(max = 20, message = "El número de documento no puede exceder 20 caracteres")
    private String numeroDocumento;

    @Email(message = "El email no es válido")
    private String email;

    private String telefono;

    private String direccion;

    private Integer idCiudad;

    private String estado;

    public ClienteRequestDTO() {}

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String v) { this.tipoDocumento = v; }
    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String v) { this.numeroDocumento = v; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public Integer getIdCiudad() { return idCiudad; }
    public void setIdCiudad(Integer idCiudad) { this.idCiudad = idCiudad; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
