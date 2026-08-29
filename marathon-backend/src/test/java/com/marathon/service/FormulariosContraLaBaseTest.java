package com.marathon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.marathon.dto.ciudad.CiudadRequestDTO;
import com.marathon.dto.ciudad.CiudadResponseDTO;
import com.marathon.dto.producto.ProductoRequestDTO;
import com.marathon.dto.producto.ProductoResponseDTO;
import com.marathon.model.Bodega;
import com.marathon.soporte.FixturaVenta;

import jakarta.validation.constraints.Size;

/**
 * F85 — lo que el formulario pide y lo que la base guarda son lo mismo.
 *
 * <p><b>De dónde sale.</b> El dueño pidió comprobar «que los campos que están en
 * la base sean los que los formularios del sistema piden». Se recorrieron los 27
 * formularios de alta y edición contra las columnas, y salieron cuatro cosas.
 * Las que se podían arreglar sin decidir nada de negocio están arregladas y
 * fijadas aquí; el resto está anotado en {@code docs/PENDIENTE.md}.
 *
 * <p><b>La peor, con diferencia:</b> la ficha de producto exigía un «precio de
 * compra», lo tiraba, y en la lista enseñaba el precio de <b>venta</b> en su
 * lugar ({@code dto.setPrecioCompra(producto.getPrecio())}). O sea que el margen
 * de todos los productos salía exactamente cero, y ningún producto dado de alta
 * por la pantalla llegaba a tener proveedor principal ni precio de compra —eso
 * sí lo tenían los 105 del poblado inicial, así que el fallo no se veía mirando
 * los datos viejos—.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("F85 · el formulario pide lo que la base guarda")
class FormulariosContraLaBaseTest {

    @Autowired private ProductoService productoService;
    @Autowired private CiudadService ciudadService;
    @Autowired private PickingService pickingService;
    @Autowired private FixturaVenta fixtura;
    @Autowired private JdbcTemplate jdbc;

    private Bodega bodega;
    private Integer idProveedor;
    private Integer idProductoCreado;
    private Integer idCiudadCreada;

    @BeforeEach
    void preparar() {
        fixtura.empezar();
        bodega = fixtura.bodegaConStock("F85", 10);
        // La fixtura crea el proveedor al pedirle una orden de compra, y lo
        // limpia ella. Aqui solo hace falta su identificador.
        fixtura.ordenCompraRecibida(1, new BigDecimal("1.00"), bodega);
        idProveedor = jdbc.queryForObject(
                "select id_proveedor from proveedor where nombre = ?",
                Integer.class, FixturaVenta.MARCA + "proveedor");
    }

    @AfterEach
    void limpiar() {
        // Lo que crea esta prueba lo borra esta prueba: la fixtura solo conoce
        // su propio producto y su propia ciudad.
        if (idProductoCreado != null) {
            jdbc.update("delete from producto_proveedor where id_producto = ?", idProductoCreado);
            jdbc.update("delete from producto where id_producto = ?", idProductoCreado);
            idProductoCreado = null;
        }
        if (idCiudadCreada != null) {
            jdbc.update("delete from ciudad where id_ciudad = ?", idCiudadCreada);
            idCiudadCreada = null;
        }
        fixtura.limpiar();
    }

    private ProductoRequestDTO productoBase(String sufijo) {
        ProductoRequestDTO dto = new ProductoRequestDTO();
        dto.setNombre(FixturaVenta.MARCA + "prod-f85-" + sufijo);
        dto.setPrecioVenta(new BigDecimal("100.00"));
        dto.setIdCategoria(fixtura.getIdCategoriaEnUso());
        dto.setIdUnidadMedida(fixtura.getIdUnidadEnUso());
        dto.setOrigen("comprado");
        return dto;
    }

    // -----------------------------------------------------------------------
    // El precio de compra
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el precio de compra se guarda con el proveedor y NO es el de venta")
    void elPrecioDeCompraEsElDelProveedor() {
        ProductoRequestDTO dto = productoBase("a");
        dto.setPrecioCompra(new BigDecimal("60.00"));
        dto.setProveedorIds(List.of(idProveedor));

        ProductoResponseDTO creado = productoService.crear(dto);
        idProductoCreado = creado.getIdProducto();

        // Lo que devuelve la pantalla.
        assertThat(creado.getPrecioVenta()).isEqualByComparingTo("100.00");
        assertThat(creado.getPrecioCompra())
                .as("antes aqui salia 100.00, o sea el precio de venta, y el margen era cero")
                .isEqualByComparingTo("60.00");

        // Y donde ha quedado de verdad.
        BigDecimal enLaBase = jdbc.queryForObject(
                "select precio_compra from producto_proveedor where id_producto = ?",
                BigDecimal.class, idProductoCreado);
        assertThat(enLaBase).isEqualByComparingTo("60.00");

        Boolean principal = jdbc.queryForObject(
                "select es_proveedor_principal from producto_proveedor where id_producto = ?",
                Boolean.class, idProductoCreado);
        assertThat(principal)
                .as("el unico proveedor del producto es el principal; antes se marcaba siempre false")
                .isTrue();

        // Y el listado dice lo mismo que la ficha: no se recalcula por otro camino.
        assertThat(productoService.listar(0, 200, FixturaVenta.MARCA + "prod-f85", null,
                                          null, null, null)
                        .getContent())
                .filteredOn(p -> p.getIdProducto().equals(idProductoCreado))
                .singleElement()
                .extracting(ProductoResponseDTO::getPrecioCompra)
                .satisfies(v -> assertThat((BigDecimal) v).isEqualByComparingTo("60.00"));
    }

    @Test
    @DisplayName("sin proveedor no hay precio de compra, y sale nulo — no cero ni el de venta")
    void sinProveedorNoHayPrecioDeCompra() {
        ProductoRequestDTO dto = productoBase("b");
        dto.setPrecioCompra(new BigDecimal("60.00"));   // se escribe, pero no hay a quien

        ProductoResponseDTO creado = productoService.crear(dto);
        idProductoCreado = creado.getIdProducto();

        assertThat(creado.getPrecioCompra())
                .as("nulo es «no se le compra a nadie»; cero seria «se compra gratis»")
                .isNull();
        assertThat(creado.getPrecioVenta()).isEqualByComparingTo("100.00");
    }

    // -----------------------------------------------------------------------
    // La region de la ciudad
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("la región de una ciudad nueva se guarda y llega hasta la cola de empaque")
    void laRegionViajaDesdeElFormulario() {
        CiudadRequestDTO nueva = new CiudadRequestDTO();
        nueva.setNombre(FixturaVenta.MARCA + "ciudad-f85");
        nueva.setRegion("Oriente");

        CiudadResponseDTO creada = ciudadService.crear(nueva);
        idCiudadCreada = creada.getIdCiudad();

        assertThat(creada.getRegion())
                .as("antes el formulario ni siquiera tenia el campo: nacia sin region")
                .isEqualTo("Oriente");

        // Y donde importa: el cliente del pedido se muda a esa ciudad y el
        // destino del empaque lo dice sin que nadie lo teclee.
        jdbc.update("update cliente set id_ciudad = ? where id_cliente = ?",
                    idCiudadCreada, fixtura.getIdCliente());
        var pedido = fixtura.pedidoListoParaEmpacar(1);

        var enCola = pickingService.listarPedidosParaEmpacar(0, 50).getContent().stream()
                .filter(p -> pedido.getIdPedido().equals(p.getIdPedido()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("El pedido no aparece en la cola de empaque"));
        assertThat(enCola.getRegionDestino()).isEqualTo("Oriente");

        // La ciudad vuelve a su sitio antes de que la fixtura intente borrarla.
        jdbc.update("update cliente set id_ciudad = (select id_ciudad from ciudad "
                  + "where nombre = ? ) where id_cliente = ?",
                    FixturaVenta.MARCA + "ciudad", fixtura.getIdCliente());
    }

    @Test
    @DisplayName("una ciudad puede quedarse sin clasificar, pero no con la región en blanco")
    void laRegionEnBlancoEsNula() {
        CiudadRequestDTO nueva = new CiudadRequestDTO();
        nueva.setNombre(FixturaVenta.MARCA + "ciudad-f85");
        nueva.setRegion("");   // el desplegable sin elegir manda cadena vacia

        CiudadResponseDTO creada = ciudadService.crear(nueva);
        idCiudadCreada = creada.getIdCiudad();

        assertThat(creada.getRegion())
                .as("la cadena vacia habria chocado con chk_ciudad_region y la pantalla "
                  + "habria recibido un error hablando de datos duplicados")
                .isNull();
    }

    // -----------------------------------------------------------------------
    // Las longitudes
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("el máximo que acepta el formulario es el que aguanta la columna")
    void lasLongitudesCoinciden() throws Exception {
        // Por que esta prueba existe: sin @Size, un nombre de 200 caracteres
        // llega a PostgreSQL, salta por longitud (22001) y GlobalExceptionHandler
        // lo traduce a «La operacion entra en conflicto con datos existentes.
        // Puede que el registro ya exista». Que no es lo que pasa, y manda a
        // quien lo lee a buscar un duplicado que no hay.
        record Campo(Class<?> dto, String campo, String tabla, String columna) {}

        List<Campo> campos = List.of(
            new Campo(com.marathon.dto.producto.ProductoRequestDTO.class, "nombre", "producto", "nombre"),
            new Campo(com.marathon.dto.proveedor.ProveedorRequestDTO.class, "nombre", "proveedor", "nombre"),
            new Campo(com.marathon.dto.bodega.BodegaRequestDTO.class, "nombre", "bodega", "nombre"),
            new Campo(com.marathon.dto.bodega.BodegaRequestDTO.class, "direccion", "bodega", "direccion"),
            new Campo(com.marathon.dto.bodega.BodegaRequestDTO.class, "responsable", "bodega", "responsable"),
            new Campo(com.marathon.dto.cliente.ClienteRequestDTO.class, "nombre", "cliente", "nombre"),
            new Campo(com.marathon.dto.cliente.ClienteRequestDTO.class, "apellido", "cliente", "apellido"),
            new Campo(com.marathon.dto.cliente.ClienteRequestDTO.class, "numeroDocumento", "cliente", "numero_documento"),
            new Campo(com.marathon.dto.ciudad.CiudadRequestDTO.class, "nombre", "ciudad", "nombre"),
            new Campo(com.marathon.dto.categoria.CategoriaRequestDTO.class, "nombre", "categoria", "nombre"),
            new Campo(com.marathon.dto.materiaprima.MateriaPrimaRequestDTO.class, "nombre", "materia_prima", "nombre"),
            new Campo(com.marathon.dto.unidadmedida.UnidadMedidaRequestDTO.class, "nombre", "unidad_medida", "nombre"),
            new Campo(com.marathon.dto.unidadmedida.UnidadMedidaRequestDTO.class, "abreviatura", "unidad_medida", "abreviatura"),
            new Campo(com.marathon.dto.facturacompra.FacturaCompraRequestDTO.class, "numeroFacturaProveedor",
                      "factura_compra", "numero_factura_proveedor"),
            new Campo(com.marathon.dto.recepcion.RecepcionMercanciaRequestDTO.class, "numeroGuiaRemision",
                      "recepcion_mercancia", "numero_guia_remision"),
            new Campo(com.marathon.dto.pago.PagoProveedorRequestDTO.class, "referencia",
                      "pago_proveedor", "referencia"),
            new Campo(com.marathon.dto.empaque.EmpaqueRequestDTO.class, "numeroHu", "pedido", "numero_hu"),
            new Campo(com.marathon.dto.rol.RolRequestDTO.class, "nombre", "rol", "nombre"),
            new Campo(com.marathon.dto.usuario.UsuarioRequestDTO.class, "correo", "usuario", "correo")
        );

        for (Campo c : campos) {
            Size size = c.dto().getDeclaredField(c.campo()).getAnnotation(Size.class);
            assertThat(size)
                    .as("%s.%s no dice cuanto cabe, y la columna %s.%s si tiene tope",
                        c.dto().getSimpleName(), c.campo(), c.tabla(), c.columna())
                    .isNotNull();

            Integer tope = jdbc.queryForObject(
                    "select character_maximum_length from information_schema.columns "
                    + "where table_schema = 'public' and table_name = ? and column_name = ?",
                    Integer.class, c.tabla(), c.columna());

            assertThat(size.max())
                    .as("%s.%s deja escribir %d y la columna %s.%s solo aguanta %d",
                        c.dto().getSimpleName(), c.campo(), size.max(),
                        c.tabla(), c.columna(), tope)
                    .isLessThanOrEqualTo(tope);
        }
    }
}
