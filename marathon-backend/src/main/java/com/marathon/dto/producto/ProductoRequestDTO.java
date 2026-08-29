package com.marathon.dto.producto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lo que el formulario de producto manda de verdad.
 *
 * <p><b>F85 — se fueron dos campos que se pedían y se tiraban.</b> Al contrastar
 * los formularios con la base salieron tres cosas que el usuario rellenaba y
 * nadie guardaba:
 *
 * <ul>
 *   <li><b>{@code codigo}</b> — {@code producto} no tiene columna de código. El
 *       que se ve en la lista lo <i>fabrica</i> la respuesta
 *       ({@code PROD-000123}, a partir del identificador). La pantalla nunca
 *       llegó a enseñar una casilla para escribirlo —el campo viajaba vacío y
 *       se ignoraba—, así que aquí no había nada que el usuario perdiera: solo
 *       un campo que prometía algo que no existe. Si algún día hace falta un
 *       código propio —el de fábrica, el de barras—, es una columna nueva con su
 *       unicidad, no este campo.</li>
 *   <li><b>{@code stockMinimo}</b> — el mínimo no es del producto, es del
 *       producto <b>en cada bodega</b> ({@code inventario.stock_minimo}). Un
 *       número suelto en la ficha no sabría a qué bodega aplicarse. Se pide
 *       desde inventario, que es donde tiene sentido.</li>
 *   <li><b>{@code precioCompra}</b> — este sí se guarda ahora. Vive en
 *       {@code producto_proveedor.precio_compra}, porque cada proveedor tiene el
 *       suyo. Antes se exigía y se tiraba, y la pantalla enseñaba el precio de
 *       <i>venta</i> en su lugar: el margen de todos los productos salía cero.
 *       Deja de ser obligatorio porque un producto <b>fabricado</b> no se compra
 *       a nadie.</li>
 * </ul>
 */
public class ProductoRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String nombre;

    private String descripcion;

    /**
     * Precio al que se le compra al proveedor principal. Opcional: un producto
     * fabricado no tiene precio de compra, tiene coste de producción.
     */
    @DecimalMin(value = "0.00", message = "El precio de compra no puede ser negativo")
    private BigDecimal precioCompra;

    @NotNull(message = "El precio de venta es obligatorio")
    @DecimalMin(value = "0.00", message = "El precio de venta no puede ser negativo")
    private BigDecimal precioVenta;

    @NotNull(message = "La categoría es obligatoria")
    private Integer idCategoria;

    @NotNull(message = "La unidad de medida es obligatoria")
    private Integer idUnidadMedida;

    @Pattern(regexp = "activo|inactivo", message = "El estado debe ser 'activo' o 'inactivo'")
    private String estado;

    @Pattern(regexp = "comprado|fabricado", message = "El origen debe ser 'comprado' o 'fabricado'")
    private String origen = "comprado";

    private List<Integer> proveedorIds;

    public ProductoRequestDTO() {}

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getPrecioCompra() { return precioCompra; }
    public void setPrecioCompra(BigDecimal precioCompra) { this.precioCompra = precioCompra; }

    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }

    public Integer getIdCategoria() { return idCategoria; }
    public void setIdCategoria(Integer idCategoria) { this.idCategoria = idCategoria; }

    public Integer getIdUnidadMedida() { return idUnidadMedida; }
    public void setIdUnidadMedida(Integer idUnidadMedida) { this.idUnidadMedida = idUnidadMedida; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }

    public List<Integer> getProveedorIds() { return proveedorIds; }
    public void setProveedorIds(List<Integer> proveedorIds) { this.proveedorIds = proveedorIds; }
}
