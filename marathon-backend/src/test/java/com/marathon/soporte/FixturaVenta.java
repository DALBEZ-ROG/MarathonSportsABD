package com.marathon.soporte;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.marathon.model.Bodega;
import com.marathon.model.Categoria;
import com.marathon.model.Ciudad;
import com.marathon.model.Cliente;
import com.marathon.model.DetallePedido;
import com.marathon.model.Inventario;
import com.marathon.model.Pedido;
import com.marathon.model.Producto;
import com.marathon.model.UnidadMedida;
import com.marathon.model.Usuario;
import com.marathon.repository.BodegaRepository;
import com.marathon.repository.CategoriaRepository;
import com.marathon.repository.CiudadRepository;
import com.marathon.repository.ClienteRepository;
import com.marathon.repository.DetallePedidoRepository;
import com.marathon.repository.InventarioRepository;
import com.marathon.repository.PedidoRepository;
import com.marathon.repository.ProductoRepository;
import com.marathon.repository.UnidadMedidaRepository;
import com.marathon.repository.UsuarioRepository;

/**
 * Monta y desmonta los datos que necesita una prueba del circuito de venta.
 *
 * <p>Las pruebas del despacho <b>no</b> pueden llevar {@code @Transactional}: lo
 * que se quiere comprobar es justamente que la transaccion del servicio revierte
 * sola cuando algo falla, y envolverlo todo en una transaccion de prueba
 * enmascararia ese comportamiento. A cambio hay que limpiar a mano, y de eso se
 * encarga {@link #limpiar()}.
 *
 * <p>Todo lo que se crea lleva el prefijo {@link #MARCA} en el nombre, de modo
 * que un resto olvidado se localiza de un vistazo:
 *
 * <pre>
 *   select * from producto where nombre like '\_\_prueba\_\_%';
 * </pre>
 */
@Component
public class FixturaVenta {

    /** Prefijo de todo lo que crea esta clase. */
    public static final String MARCA = "__prueba__";

    @Autowired private CiudadRepository ciudadRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private UnidadMedidaRepository unidadMedidaRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private BodegaRepository bodegaRepository;
    @Autowired private InventarioRepository inventarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private DetallePedidoRepository detallePedidoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JdbcTemplate jdbc;

    // --- lo creado en la prueba en curso, para poder deshacerlo ---------------
    private final List<Integer> idsInventario = new ArrayList<>();
    private final List<Integer> idsPedido = new ArrayList<>();
    private Integer idProducto;
    private Integer idCliente;
    private Integer idCategoria;
    private Integer idUnidad;
    private Integer idCiudad;
    private final List<Integer> idsBodega = new ArrayList<>();
    private final List<Integer> idsOrdenCompra = new ArrayList<>();
    private Integer idProveedor;
    private long maxLogAlEmpezar;

    private Ciudad ciudad;
    private Producto producto;
    private Cliente cliente;
    private Usuario usuario;

    /** Marca el punto de partida. Llamar en el {@code @BeforeEach}. */
    public void empezar() {
        maxLogAlEmpezar = numero(jdbc.queryForObject(
                "select coalesce(max(id_log), 0) from log_accion", Long.class));

        ciudad = new Ciudad();
        ciudad.setNombre(MARCA + "ciudad");
        ciudad.setEstado("activo");
        ciudad = ciudadRepository.save(ciudad);
        idCiudad = ciudad.getIdCiudad();

        Categoria categoria = new Categoria();
        categoria.setNombre(MARCA + "categoria");
        categoria.setDescripcion("categoria de prueba");
        categoria = categoriaRepository.save(categoria);
        idCategoria = categoria.getIdCategoria();

        UnidadMedida unidad = new UnidadMedida();
        unidad.setNombre(MARCA + "unidad");
        unidad.setAbreviatura("__pu");   // abreviatura es varchar(10): no cabe MARCA entera
        unidad = unidadMedidaRepository.save(unidad);
        idUnidad = unidad.getIdUnidadMedida();

        producto = new Producto();
        producto.setNombre(MARCA + "producto");
        producto.setDescripcion("producto de prueba");
        producto.setPrecio(new BigDecimal("100.00"));
        producto.setEstado("activo");
        producto.setOrigen("comprado");
        producto.setCategoria(categoria);
        producto.setUnidadMedida(unidad);
        producto = productoRepository.save(producto);
        idProducto = producto.getIdProducto();

        cliente = new Cliente();
        cliente.setNombre(MARCA + "cliente");
        cliente.setApellido("apellido");
        cliente.setEstado("activo");
        cliente.setCiudad(ciudad);
        cliente = clienteRepository.save(cliente);
        idCliente = cliente.getIdCliente();

        usuario = usuarioRepository.findByCorreo("admin@marathon.com")
                .orElseThrow(() -> new IllegalStateException(
                        "falta admin@marathon.com en la base de pruebas; lo siembra DataInitializer"));
    }

    /** Crea una bodega con una fila de inventario para el producto de la fixtura. */
    public Bodega bodegaConStock(String sufijo, int stock) {
        Bodega bodega = new Bodega();
        bodega.setNombre(MARCA + "bodega_" + sufijo);
        bodega.setDireccion("s/n");
        bodega.setEstado("activo");
        bodega.setCiudad(ciudad);
        bodega = bodegaRepository.save(bodega);
        idsBodega.add(bodega.getIdBodega());

        Inventario inv = new Inventario();
        inv.setProducto(producto);
        inv.setBodega(bodega);
        inv.setStockActual(stock);
        inv.setStockMinimo(0);
        inv = inventarioRepository.save(inv);
        idsInventario.add(inv.getIdInventario());

        return bodega;
    }

    /**
     * Crea un pedido en estado {@code procesado} con una sola linea del producto
     * de la fixtura, ya marcada como recogida (picking completo).
     */
    public Pedido pedidoListoParaEmpacar(int cantidad) {
        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setUsuario(usuario);
        pedido.setEstado("pendiente");
        pedido.setDescuento(BigDecimal.ZERO);
        pedido.setEsPedidoEspecial(false);
        pedido = pedidoRepository.save(pedido);
        idsPedido.add(pedido.getIdPedido());

        DetallePedido detalle = new DetallePedido();
        detalle.setPedido(pedido);
        detalle.setProducto(producto);
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(new BigDecimal("100.00"));
        detalle.setCantidadRecogida(cantidad);
        detalle.setPickingCompletado(true);
        detallePedidoRepository.save(detalle);

        pedido.setEstado("procesado");
        return pedidoRepository.save(pedido);
    }

    // --- consultas directas, sin pasar por el contexto de persistencia --------
    // Se leen con SQL a proposito: si se leyeran con los repositorios, la cache
    // de primer nivel podria devolver el valor que la prueba escribio y no el
    // que quedo en la base.

    public int stockEnBaseDe(Integer idBodega) {
        return (int) numero(jdbc.queryForObject(
                "select stock_actual from inventario where id_producto = ? and id_bodega = ?",
                Integer.class, idProducto, idBodega));
    }

    public int movimientosDe(Integer idPedido) {
        return (int) numero(jdbc.queryForObject(
                "select count(*) from movimiento_inventario where id_pedido = ?",
                Integer.class, idPedido));
    }

    /** Desempaqueta un agregado SQL que el compilador no sabe que nunca es nulo. */
    private static long numero(Number valor) {
        if (valor == null) {
            throw new IllegalStateException("la consulta de la fixtura devolvio nulo");
        }
        return valor.longValue();
    }

    public List<Integer> cantidadesMovidasDe(Integer idPedido) {
        return jdbc.queryForList(
                "select cantidad from movimiento_inventario where id_pedido = ? order by id_movimiento",
                Integer.class, idPedido);
    }

    public String estadoEnBaseDe(Integer idPedido) {
        return jdbc.queryForObject(
                "select estado from pedido where id_pedido = ?", String.class, idPedido);
    }

    public Integer getIdProducto() {
        return idProducto;
    }

    public Integer getIdCategoriaEnUso() { return idCategoria; }

    public Integer getIdUnidadEnUso() { return idUnidad; }

    public Integer getIdCliente() {
        return idCliente;
    }

    public Integer getIdUsuario() {
        return usuario.getIdUsuario();
    }

    /** Precio de catalogo del producto de la fixtura. */
    public java.math.BigDecimal precioDeCatalogo() {
        return producto.getPrecio();
    }

    /** Deja el producto de la fixtura dado de baja. */
    public void desactivarProducto() {
        producto.setEstado("inactivo");
        producto = productoRepository.save(producto);
    }

    /** Lee de la base el precio realmente persistido en las lineas de un pedido. */
    public List<java.math.BigDecimal> preciosPersistidosDe(Integer idPedido) {
        return jdbc.queryForList(
                "select precio_unitario from detalle_pedido where id_pedido = ? order by id_detalle",
                java.math.BigDecimal.class, idPedido);
    }

    public java.math.BigDecimal totalEnBaseDe(Integer idPedido) {
        return jdbc.queryForObject(
                "select total from pedido where id_pedido = ?", java.math.BigDecimal.class, idPedido);
    }

    /**
     * Crea un pedido en 'procesado' con una linea recogida DESDE una bodega
     * concreta (L4). El equivalente de pedidoListoParaEmpacar cuando importa de
     * donde salio la mercancia.
     */
    public Pedido pedidoRecogidoDesde(int cantidad, Bodega bodegaDelPicking) {
        Pedido pedido = pedidoListoParaEmpacar(cantidad);
        List<DetallePedido> lineas = detallePedidoRepository
                .findByPedidoIdPedidoOrderByIdDetalleAsc(pedido.getIdPedido());
        for (DetallePedido linea : lineas) {
            linea.setBodegaPicking(bodegaDelPicking);
            detallePedidoRepository.save(linea);
        }
        return pedido;
    }

    /** Pedido en 'procesado' con una linea TODAVIA sin recoger, para ejercitar el picking. */
    public Pedido pedidoConLineaSinRecoger(int cantidad) {
        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setUsuario(usuario);
        pedido.setEstado("pendiente");
        pedido.setDescuento(BigDecimal.ZERO);
        pedido.setEsPedidoEspecial(false);
        pedido = pedidoRepository.save(pedido);
        idsPedido.add(pedido.getIdPedido());

        DetallePedido detalle = new DetallePedido();
        detalle.setPedido(pedido);
        detalle.setProducto(producto);
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(new BigDecimal("100.00"));
        detalle.setCantidadRecogida(0);
        detalle.setPickingCompletado(false);
        detallePedidoRepository.save(detalle);

        pedido.setEstado("procesado");
        return pedidoRepository.save(pedido);
    }

    public Integer idPrimeraLineaDe(Integer idPedido) {
        return detallePedidoRepository.findByPedidoIdPedidoOrderByIdDetalleAsc(idPedido)
                .get(0).getIdDetalle();
    }

    /** Registra un pedido creado por el servicio, para que la limpieza lo borre. */
    public void seguirPedido(Integer idPedido) {
        idsPedido.add(idPedido);
    }

    // ------------------------------------------------------------------
    // Reserva de stock (F47, D-02)
    // ------------------------------------------------------------------

    /**
     * Pedido en {@code pendiente} con una linea, sin recoger. Es el punto de
     * partida de las pruebas de reserva: la reserva nace en la transicion
     * pendiente -> procesado, asi que la prueba necesita un pedido que todavia
     * no la haya hecho.
     */
    public Pedido pedidoPendiente(int cantidad) {
        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setUsuario(usuario);
        pedido.setEstado("pendiente");
        pedido.setDescuento(BigDecimal.ZERO);
        pedido.setEsPedidoEspecial(false);
        pedido = pedidoRepository.save(pedido);
        idsPedido.add(pedido.getIdPedido());

        DetallePedido detalle = new DetallePedido();
        detalle.setPedido(pedido);
        detalle.setProducto(producto);
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(new BigDecimal("100.00"));
        detalle.setCantidadRecogida(0);
        detalle.setPickingCompletado(false);
        detallePedidoRepository.save(detalle);

        return pedido;
    }

    /** Unidades activas reservadas por un pedido, leidas directamente de la base. */
    public int reservadoActivoDe(Integer idPedido) {
        return (int) numero(jdbc.queryForObject(
                "select coalesce(sum(cantidad), 0) from reserva_stock "
                + "where id_pedido = ? and estado = 'activa'",
                Integer.class, idPedido));
    }

    /** Estados de las reservas de un pedido, en orden de creacion. */
    public List<String> estadosReservaDe(Integer idPedido) {
        return jdbc.queryForList(
                "select estado from reserva_stock where id_pedido = ? order by id_reserva",
                String.class, idPedido);
    }

    /**
     * Retrasa la fecha de las reservas activas de un pedido, para poder probar
     * el informe de vencidas sin esperar siete dias.
     */
    public void envejecerReservasDe(Integer idPedido, int dias) {
        jdbc.update("update reserva_stock set fecha_reserva = fecha_reserva - make_interval(days => ?) "
                + "where id_pedido = ? and estado = 'activa'", dias, idPedido);
    }

    /** Unidades activas reservadas del producto de la fixtura, sea cual sea el pedido. */
    public int reservadoTotalDelProducto() {
        return (int) numero(jdbc.queryForObject(
                "select coalesce(sum(cantidad), 0) from reserva_stock "
                + "where id_producto = ? and estado = 'activa'",
                Integer.class, idProducto));
    }

    /**
     * Borra todo lo creado, hijos primero. Llamar en el {@code @AfterEach}.
     *
     * <p>El orden importa: casi todas las claves foraneas del esquema son
     * {@code ON DELETE RESTRICT}, asi que un borrado en el orden equivocado no
     * corrompe nada — simplemente falla.
     */
    /**
     * Una orden de compra con mercancia YA RECIBIDA, para poder facturarla.
     *
     * <p>La necesita la prueba de D-36: contrastar el importe de la factura con
     * lo recibido exige que haya algo recibido con lo que contrastar.
     *
     * <p>Se inserta con {@code JdbcTemplate} y no por los repositorios a
     * proposito: son cuatro tablas de las que aqui solo interesan seis columnas,
     * y {@code orden_compra_detalle.subtotal} es GENERATED ALWAYS —Hibernate
     * intentaria escribirla y PostgreSQL lo rechazaria—.
     *
     * @return el id de la orden, cuyo valor recibido es {@code cantidad * precio}
     */
    public Integer ordenCompraRecibida(int cantidad, java.math.BigDecimal precio, Bodega bodega) {
        if (idProveedor == null) {
            idProveedor = jdbc.queryForObject(
                    "insert into proveedor (nombre, estado) values (?, ?) returning id_proveedor",
                    Integer.class, MARCA + "proveedor", "activo");
        }

        Integer idOrden = jdbc.queryForObject(
                "insert into orden_compra (id_proveedor, id_usuario_solicitante, estado) "
                + "values (?, ?, ?) returning id_orden_compra",
                Integer.class, idProveedor, usuario.getIdUsuario(), "recibida_completa");
        idsOrdenCompra.add(idOrden);

        jdbc.update("insert into orden_compra_detalle "
                + "(id_orden_compra, tipo_item, id_producto, cantidad, precio_unitario, cantidad_recibida) "
                + "values (?, ?, ?, ?, ?, ?)",
                idOrden, "producto", idProducto, cantidad, precio, cantidad);

        jdbc.update("insert into recepcion_mercancia "
                + "(id_orden_compra, id_usuario_receptor, id_bodega) values (?, ?, ?)",
                idOrden, usuario.getIdUsuario(), bodega.getIdBodega());

        return idOrden;
    }

    public void limpiar() {
        for (Integer idPedido : idsPedido) {
            // F47: reserva_stock apunta a pedido y a producto con ON DELETE
            // RESTRICT, asi que va antes que los dos.
            jdbc.update("delete from reserva_stock where id_pedido = ?", idPedido);
            jdbc.update("delete from movimiento_inventario where id_pedido = ?", idPedido);
            jdbc.update("delete from comprobante_interno where id_pedido = ?", idPedido);
            jdbc.update("delete from detalle_pedido where id_pedido = ?", idPedido);
            jdbc.update("delete from pedido where id_pedido = ?", idPedido);
        }
        for (Integer idOrden : idsOrdenCompra) {
            // De la hoja a la raiz: la cuenta por pagar cuelga de la factura, la
            // factura de la orden, y la recepcion y el detalle tambien.
            jdbc.update("delete from cuenta_por_pagar where id_factura_compra in "
                    + "(select id_factura_compra from factura_compra where id_orden_compra = ?)", idOrden);
            jdbc.update("delete from factura_compra where id_orden_compra = ?", idOrden);
            jdbc.update("delete from recepcion_mercancia where id_orden_compra = ?", idOrden);
            jdbc.update("delete from orden_compra_detalle where id_orden_compra = ?", idOrden);
            jdbc.update("delete from orden_compra where id_orden_compra = ?", idOrden);
        }
        if (idProveedor != null) {
            jdbc.update("delete from producto_proveedor where id_proveedor = ?", idProveedor);
            jdbc.update("delete from proveedor where id_proveedor = ?", idProveedor);
        }
        for (Integer idInv : idsInventario) {
            jdbc.update("delete from movimiento_inventario where id_inventario = ? or id_inventario_destino = ?",
                    idInv, idInv);
            jdbc.update("delete from historial_inventario where id_inventario = ?", idInv);
            jdbc.update("delete from inventario where id_inventario = ?", idInv);
        }

        if (idProducto != null) {
            jdbc.update("delete from producto where id_producto = ?", idProducto);
        }
        for (Integer idBodega : idsBodega) {
            jdbc.update("delete from bodega where id_bodega = ?", idBodega);
        }
        if (idCliente != null) {
            jdbc.update("delete from cliente where id_cliente = ?", idCliente);
        }
        if (idCategoria != null) {
            jdbc.update("delete from categoria where id_categoria = ?", idCategoria);
        }
        if (idUnidad != null) {
            jdbc.update("delete from unidad_medida where id_unidad_medida = ?", idUnidad);
        }
        if (idCiudad != null) {
            jdbc.update("delete from ciudad where id_ciudad = ?", idCiudad);
        }
        // La bitacora no cuelga de la fixtura: se borra por marca de tiempo.
        jdbc.update("delete from log_accion where id_log > ?", maxLogAlEmpezar);
        // auditoria_cambios NO se limpia, y es correcto que no se pueda: el
        // primer intento de hacerlo fallo con "permiso denegado a la tabla
        // auditoria_cambios". usr_admin_marathon tiene INSERT via trigger pero
        // no DELETE, que es exactamente lo que debe ser una traza de auditoria.
        // Las filas que dejen las pruebas se quedan; son de solo anadir y viven
        // unicamente en la base de pruebas.

        idsInventario.clear();
        idsPedido.clear();
        idsBodega.clear();
        idsOrdenCompra.clear();
        idProveedor = null;
        idProducto = null;
        idCliente = null;
        idCategoria = null;
        idUnidad = null;
        idCiudad = null;
    }
}
