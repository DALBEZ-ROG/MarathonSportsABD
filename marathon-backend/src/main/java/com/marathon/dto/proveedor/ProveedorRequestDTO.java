package com.marathon.dto.proveedor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lo que el formulario de proveedor manda.
 *
 * <p><b>F85 — se fue {@code idCiudad}.</b> El formulario pedía la ciudad del
 * proveedor y la respuesta declaraba {@code idCiudad} y {@code ciudadNombre},
 * pero la tabla {@code proveedor} <b>no tiene columna de ciudad</b>: lo que se
 * escribía se perdía y lo que se devolvía era siempre nulo. Poner la ciudad del
 * proveedor es una decisión de negocio con su columna y su clave ajena, no un
 * campo suelto en una pantalla; queda anotado en PENDIENTE.md.
 *
 * <p><b>Lo que sí se guarda, y dónde.</b> El RUC va cifrado en
 * {@code proveedor.contacto_enc} —una columna que se llama «contacto»—, así que
 * <b>no se puede buscar un proveedor por su RUC</b>. Es lo contrario de lo que
 * se decidió para el cliente en la F73, donde el documento se dejó sin cifrar a
 * propósito para poder buscarlo. También está anotado.
 */
public class ProveedorRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String nombre;

    @Size(max = 13, message = "El RUC no puede exceder 13 caracteres")
    private String ruc;

    private String direccion;
    private String telefono;

    @Email(message = "El correo no es válido")
    private String email;

    @Pattern(regexp = "activo|inactivo", message = "El estado debe ser 'activo' o 'inactivo'")
    private String estado;

    public ProveedorRequestDTO() {}

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }


    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
