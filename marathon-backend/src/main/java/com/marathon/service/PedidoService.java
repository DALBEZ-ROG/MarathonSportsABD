package com.marathon.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.config.Permisos;
import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.pedido.CambioEstadoDTO;
import com.marathon.dto.pedido.DetallePedidoItemDTO;
import com.marathon.dto.pedido.DetallePedidoResponseDTO;
import com.marathon.dto.pedido.PedidoRequestDTO;
import com.marathon.dto.pedido.PedidoResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Cliente;
import com.marathon.model.DetallePedido;
import com.marathon.model.Pedido;
import com.marathon.model.Producto;
import com.marathon.model.Usuario;
import com.marathon.repository.ClienteRepository;
import com.marathon.repository.DetallePedidoRepository;
import com.marathon.repository.PedidoRepository;
import com.marathon.repository.ProductoRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;
    private final LogService logService;
    private final PickingService pickingService;
    private final ReservaStockService reservaStockService;

    @PersistenceContext
    private EntityManager entityManager;

    public PedidoService(PedidoRepository pedidoRepository,
                         DetallePedidoRepository detallePedidoRepository,
                         ClienteRepository clienteRepository,
                         UsuarioRepository usuarioRepository,
                         ProductoRepository productoRepository,
                         LogService logService,
                         PickingService pickingService,
                         ReservaStockService reservaStockService) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoRepository = productoRepository;
        this.logService = logService;
        this.pickingService = pickingService;
        this.reservaStockService = reservaStockService;
    }

    public PageResponseDTO<PedidoResponseDTO> listar(int page, int size, String estado,
                                                      String fechaDesde, String fechaHasta,
                                                      String busqueda) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "idPedido"));

        // Topes por defecto en vez de null: un parametro de fecha nulo que solo
        // aparece en un «? IS NULL» deja a PostgreSQL sin poder deducir su tipo
        // y la consulta falla. Es el mismo recurso que ya usa
        // EmpaqueService.listarDespachados.
        LocalDateTime desde = (fechaDesde != null && !fechaDesde.isEmpty())
                ? LocalDate.parse(fechaDesde).atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime hasta = (fechaHasta != null && !fechaHasta.isEmpty())
                ? LocalDate.parse(fechaHasta).atTime(LocalTime.MAX)
                : LocalDateTime.of(2999, 12, 31, 23, 59);

        // F54: las cuatro ramas if/else que habia aqui no admitian buscar. Con
        // 230.000 pedidos, encontrar uno era pasar paginas. Ahora es una sola
        // consulta con todos los filtros opcionales.
        Page<Pedido> result = pedidoRepository.buscar(
                Filtros.vacioComoNulo(estado), desde, hasta, Filtros.numeroDePedido(busqueda), pageable);

        List<PedidoResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public PageResponseDTO<PedidoResponseDTO> listarEspeciales(int page, int size, String tipoEspecial) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("fechaLimiteEntrega").nullsLast()));
        Page<Pedido> result;

        if (tipoEspecial != null && !tipoEspecial.isEmpty()) {
            result = pedidoRepository.findByEsPedidoEspecialTrueAndTipoEspecial(tipoEspecial, pageable);
        } else {
            result = pedidoRepository.findByEsPedidoEspecialTrue(pageable);
        }

        List<PedidoResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public PedidoResponseDTO obtener(Integer id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));
        PedidoResponseDTO dto = toDTO(pedido);
        List<DetallePedido> detalles = detallePedidoRepository.findByPedidoIdPedido(id);
        dto.setDetalles(detalles.stream().map(this::toDetalleDTO).collect(Collectors.toList()));
        return dto;
    }

    @Transactional
    public PedidoResponseDTO crear(PedidoRequestDTO dto, Integer idUsuarioActual) {
        Cliente cliente = clienteRepository.findById(dto.getIdCliente())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", dto.getIdCliente()));
        if (!"activo".equals(cliente.getEstado())) {
            throw new ValidationException("El cliente no está activo");
        }

        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        // --------------------------------------------------------------------
        // L8 (D-19): el descuento tiene tope, y pasarse es un error, no un 0.
        // --------------------------------------------------------------------
        // El DTO no valida 'descuento' y el trigger aplica GREATEST(..., 0), asi
        // que un descuento mayor que el subtotal no daba error: dejaba el pedido
        // en total 0 y nadie se enteraba. Uno negativo inflaba el total y moria
        // como un 500 desde chk_pedido_descuento.
        //
        // El subtotal se calcula aqui, a precio de catalogo, porque es el mismo
        // precio que la L3 va a persistir en las lineas.
        BigDecimal descuento = dto.getDescuento() != null ? dto.getDescuento() : BigDecimal.ZERO;
        if (descuento.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("El descuento no puede ser negativo");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        Map<Integer, Producto> productos = new LinkedHashMap<>();
        Map<Integer, Integer> demanda = new LinkedHashMap<>();
        for (DetallePedidoItemDTO item : dto.getDetalles()) {
            Producto p = productoRepository.findById(item.getIdProducto())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", item.getIdProducto()));

            // D-24: no se vende un producto dado de baja. La comprobacion vivia
            // en el bucle de abajo, el que guarda las lineas; se sube aqui
            // porque la F47 metio en medio la comprobacion de existencias, y un
            // articulo retirado del catalogo tiene que decir "no esta activo" y
            // no "no hay existencias" — son dos problemas distintos y el segundo
            // manda a quien lo lee a mirar el almacen para nada.
            if (!"activo".equals(p.getEstado())) {
                throw new ValidationException(
                        "El producto '" + p.getNombre() + "' no está activo y no se puede vender");
            }

            subtotal = subtotal.add(p.getPrecio().multiply(BigDecimal.valueOf(item.getCantidad())));
            productos.putIfAbsent(p.getIdProducto(), p);
            // Se agrupa: dos lineas de 6 del mismo articulo son una demanda de
            // 12, no dos demandas de 6 contra el mismo disponible.
            demanda.merge(p.getIdProducto(), item.getCantidad(), (a, b) -> a + b);
        }
        if (descuento.compareTo(subtotal) > 0) {
            throw new ValidationException("El descuento (" + descuento
                    + ") no puede superar el subtotal del pedido (" + subtotal + ")");
        }

        // --------------------------------------------------------------------
        // F47 (D-02): crear un pedido MIRA el inventario.
        // --------------------------------------------------------------------
        // Hasta aqui, crear() validaba cliente y producto por id y no consultaba
        // 'inventario' en ningun momento: se podian crear cien pedidos de un
        // articulo con tres unidades. Ahora se comprueba el DISPONIBLE, que no es
        // el stock: es el stock menos lo que otros pedidos ya procesados tienen
        // comprometido (ReservaStockService).
        //
        // Comprobar no es reservar. Aqui no se retiene nada — la retencion es al
        // pasar a 'procesado'—, asi que dos pedidos creados a la vez sobre las
        // mismas unidades pasan los dos. Es deliberado: el que retiene es el que
        // entra al almacen, y hay 16.099 pedidos viviendo en 'pendiente' que
        // bloquearian mercancia para siempre si la creacion retuviera.
        //
        // EXCEPCION, los pedidos especiales. Un pedido 'personalizado' o
        // 'corporativo' existe precisamente para prepararse o fabricarse: tiene
        // fecha_limite_entrega y el sistema tiene ordenes de produccion para
        // cumplirlo. Bloquearlo por falta de stock hoy romperia un flujo que
        // funciona, asi que se crea y el deficit queda dicho en la bitacora en
        // vez de callado.
        boolean esEspecial = Boolean.TRUE.equals(dto.getEsPedidoEspecial());
        if (esEspecial && (dto.getTipoEspecial() == null || dto.getTipoEspecial().trim().isEmpty())) {
            throw new ValidationException("Debe especificar el tipo de pedido especial");
        }

        Map<Producto, Integer> faltantes = reservaStockService.faltantesDe(productos, demanda);
        if (!faltantes.isEmpty()) {
            String detalle = reservaStockService.describirFaltantes(faltantes);
            if (!esEspecial) {
                throw new ValidationException("No hay existencias disponibles para el pedido. " + detalle
                        + ". El disponible es el stock menos lo ya comprometido por pedidos en proceso.");
            }
            logService.registrar(idUsuarioActual, "pedidos", "crear_sin_stock",
                    "Pedido especial creado por encima del disponible. " + detalle, null);
        }

        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setUsuario(usuario);
        pedido.setEstado("pendiente");
        pedido.setDescuento(descuento);

        pedido.setEsPedidoEspecial(esEspecial);
        pedido.setTipoEspecial(esEspecial ? dto.getTipoEspecial() : null);
        pedido.setNotaEspecial(esEspecial ? dto.getNotaEspecial() : null);
        pedido.setFechaLimiteEntrega(esEspecial ? dto.getFechaLimiteEntrega() : null);

        pedido = pedidoRepository.save(pedido);

        for (DetallePedidoItemDTO item : dto.getDetalles()) {
            Producto producto = productoRepository.findById(item.getIdProducto())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", item.getIdProducto()));

            // El estado del producto (D-24) ya se comprobo en el bucle de
            // arriba, antes de mirar existencias. No se repite aqui.

            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedido);
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            // ------------------------------------------------------------------
            // L3 (D-34): el precio lo pone el CATALOGO, no quien llama.
            // ------------------------------------------------------------------
            // Hasta aqui era detalle.setPrecioUnitario(item.getPrecioUnitario()):
            // el producto se cargaba de la base solo para asociarlo por id, y su
            // precio se ignoraba. Un POST con "precioUnitario": 0.01 sobre un
            // articulo de 200 creaba un pedido valido de 0.01, y las tres
            // defensas del motor (fn_recalcular_total_pedido_stmt,
            // fn_proteger_total_pedido, fn_validar_total_comprobante) confirmaban
            // fielmente ese importe inventado, porque no tienen forma de saber
            // cual era el precio de catalogo.
            //
            // item.getPrecioUnitario() se sigue aceptando en el DTO pero se
            // ignora: quitarlo del contrato ahora romperia al frontend, que
            // todavia lo envia. Se retira cuando el front deje de mandarlo.
            detalle.setPrecioUnitario(producto.getPrecio());
            detallePedidoRepository.save(detalle);
        }

        entityManager.flush();
        entityManager.clear();
        pedido = pedidoRepository.findById(pedido.getIdPedido()).orElseThrow();

        PedidoResponseDTO response = toDTO(pedido);
        List<DetallePedido> detalles = detallePedidoRepository.findByPedidoIdPedido(pedido.getIdPedido());
        response.setDetalles(detalles.stream().map(this::toDetalleDTO).collect(Collectors.toList()));

        logService.registrar(idUsuarioActual, "pedidos", "crear",
                "Pedido #" + pedido.getIdPedido() + " creado. Total: $" + pedido.getTotal(), null);

        return response;
    }

    @Transactional
    public PedidoResponseDTO cambiarEstado(Integer id, CambioEstadoDTO dto) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));

        String estadoActual = pedido.getEstado();
        String nuevoEstado = dto.getEstado();

        validarTransicion(estadoActual, nuevoEstado);

        // F48 (D-13): anular y cambiar de estado son dos permisos distintos, y
        // caen en la misma llamada HTTP, asi que la distincion no cabe en un
        // @PreAuthorize del controlador. Hasta aqui la matriz los separaba y
        // nadie la miraba.
        if ("anulado".equals(nuevoEstado)) {
            Permisos.exigirSiHaySesion("pedidos:anular", "anular pedidos");
        } else {
            Permisos.exigirSiHaySesion("pedidos:editar", "cambiar el estado de un pedido");
        }

        // La transición procesado→enviado requiere picking completo.
        // El flujo normal pasa por EmpaqueService, que valida y asigna HU/transportista.
        if ("procesado".equals(estadoActual) && "enviado".equals(nuevoEstado)) {
            String estadoPicking = pickingService.verificarEstadoPicking(id);
            if (!"completo".equals(estadoPicking)) {
                throw new ValidationException(
                    "No se puede enviar el pedido: el picking no está completo. " +
                    "Usa el módulo de empaque para procesar el despacho correctamente.");
            }
        }

        // --------------------------------------------------------------------
        // F47 (D-02): aqui es donde el pedido RETIENE la mercancia.
        // --------------------------------------------------------------------
        // 'procesado' es el estado en el que el pedido entra al almacen: es el
        // requisito del picking y del empaque. Reservar aqui —y no al crear— es
        // la decision de negocio del 2026-08-27, y su motivo es que hay 16.099
        // pedidos en 'pendiente': si la creacion retuviera, cada pedido
        // abandonado bloquearia unidades hasta que alguien lo anulara.
        //
        // reservarPara() aborta la transaccion entera si alguna linea no cabe,
        // asi que el pedido NO llega a quedar en 'procesado' sin su reserva. El
        // orden importa: se reserva ANTES de guardar el estado nuevo.
        if ("pendiente".equals(estadoActual) && "procesado".equals(nuevoEstado)) {
            reservaStockService.reservarPara(pedido);
        }

        // Anular suelta lo retenido. Es el unico camino automatico de vuelta:
        // el otro es el despacho, que la consume (EmpaqueService). Un pedido
        // 'pendiente' anulado no tiene reservas y esto no hace nada, que es
        // correcto y no un caso especial.
        if ("anulado".equals(nuevoEstado)) {
            int liberadas = reservaStockService.liberarDe(id, "Anulacion del pedido #" + id);
            if (liberadas > 0) {
                logService.registrar(logService.idUsuarioActual(), "pedidos", "liberar_reserva",
                        "Pedido #" + id + " anulado: liberadas " + liberadas + " reservas de stock", null);
            }
        }

        pedido.setEstado(nuevoEstado);
        pedido = pedidoRepository.save(pedido);

        // L11 (D-18): antes se pasaba null literal, y la transición de estado
        // —incluida la anulación— quedaba en la bitácora sin responsable.
        // idUsuarioActual() lo resuelve del contexto de seguridad; no hizo falta
        // cambiar ninguna firma.
        logService.registrar(logService.idUsuarioActual(), "pedidos", "cambio_estado",
                "Pedido #" + id + ": " + estadoActual + " → " + nuevoEstado, null);

        return toDTO(pedido);
    }

    private void validarTransicion(String estadoActual, String nuevoEstado) {
        boolean valida = false;

        switch (estadoActual) {
            case "pendiente":
                valida = "procesado".equals(nuevoEstado) || "anulado".equals(nuevoEstado);
                break;
            case "procesado":
                valida = "enviado".equals(nuevoEstado) || "anulado".equals(nuevoEstado);
                break;
            case "enviado":
                valida = "entregado".equals(nuevoEstado);
                break;
            case "entregado":
            case "anulado":
                valida = false;
                break;
            default:
                valida = false;
        }

        if (!valida) {
            throw new ValidationException(
                    "No se puede cambiar el estado de '" + estadoActual + "' a '" + nuevoEstado + "'");
        }
    }

    /**
     * Construye el DTO con detalles YA cargados, sin volver a la base (L16, D-28).
     *
     * <p>obtener(id) sigue existiendo para el caso de un solo pedido; esto es
     * para cuando quien llama ya trae la pagina entera y sus lineas.
     */
    public PedidoResponseDTO aDTOConDetalles(Pedido pedido, List<DetallePedido> detalles) {
        PedidoResponseDTO dto = toDTO(pedido);
        dto.setDetalles(detalles.stream().map(this::toDetalleDTO).collect(Collectors.toList()));
        return dto;
    }

    private PedidoResponseDTO toDTO(Pedido pedido) {
        PedidoResponseDTO dto = new PedidoResponseDTO();
        dto.setIdPedido(pedido.getIdPedido());
        dto.setNumeroPedido(String.format("PED-%06d", pedido.getIdPedido()));
        dto.setFechaPedido(pedido.getFechaPedido());
        dto.setTotal(pedido.getTotal());
        dto.setEstado(pedido.getEstado());
        if (pedido.getCliente() != null) {
            dto.setIdCliente(pedido.getCliente().getIdCliente());
            dto.setClienteNombre(pedido.getCliente().getNombre() + " " + pedido.getCliente().getApellido());
        }
        if (pedido.getUsuario() != null) {
            dto.setIdUsuario(pedido.getUsuario().getIdUsuario());
            dto.setUsuarioNombre(pedido.getUsuario().getNombre() + " " + pedido.getUsuario().getApellido());
        }
        dto.setEsPedidoEspecial(pedido.getEsPedidoEspecial());
        dto.setTipoEspecial(pedido.getTipoEspecial());
        dto.setNotaEspecial(pedido.getNotaEspecial());
        dto.setFechaLimiteEntrega(pedido.getFechaLimiteEntrega());
        dto.setNumeroHu(pedido.getNumeroHu());
        dto.setTransportista(pedido.getTransportistaNombre());
        dto.setRegionDestino(pedido.getRegionDestino());
        dto.setFechaEmpaque(pedido.getFechaEmpaque());
        return dto;
    }

    private DetallePedidoResponseDTO toDetalleDTO(DetallePedido detalle) {
        DetallePedidoResponseDTO dto = new DetallePedidoResponseDTO();
        dto.setIdDetalle(detalle.getIdDetalle());
        dto.setCantidad(detalle.getCantidad());
        dto.setPrecioUnitario(detalle.getPrecioUnitario());
        dto.setSubtotal(detalle.getSubtotal());
        if (detalle.getProducto() != null) {
            dto.setProductoId(detalle.getProducto().getIdProducto());
            dto.setProductoNombre(detalle.getProducto().getNombre());
        }
        return dto;
    }
}
