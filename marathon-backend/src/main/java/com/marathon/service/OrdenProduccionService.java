package com.marathon.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.produccion.CompletarProduccionDTO;
import com.marathon.dto.produccion.ConsumoRealItemDTO;
import com.marathon.dto.produccion.DisponibilidadMaterialDTO;
import com.marathon.dto.produccion.OrdenProduccionRequestDTO;
import com.marathon.dto.produccion.OrdenProduccionResponseDTO;
import com.marathon.dto.produccion.VerificacionDisponibilidadDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.model.Inventario;
import com.marathon.model.ListaMateriales;
import com.marathon.model.MateriaPrima;
import com.marathon.model.MovimientoInventario;
import com.marathon.model.MovimientoMateriaPrima;
import com.marathon.model.OrdenProduccion;
import com.marathon.model.OrdenProduccionConsumo;
import com.marathon.model.Producto;
import com.marathon.model.Usuario;
import com.marathon.repository.BodegaRepository;
import com.marathon.repository.InventarioRepository;
import com.marathon.repository.ListaMaterialesRepository;
import com.marathon.repository.MateriaPrimaRepository;
import com.marathon.repository.MovimientoInventarioRepository;
import com.marathon.repository.MovimientoMateriaPrimaRepository;
import com.marathon.repository.OrdenProduccionConsumoRepository;
import com.marathon.repository.OrdenProduccionRepository;
import com.marathon.repository.ProductoRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class OrdenProduccionService {

    private final OrdenProduccionRepository ordenRepository;
    private final OrdenProduccionConsumoRepository consumoRepository;
    private final ListaMaterialesRepository listaMaterialesRepository;
    private final ProductoRepository productoRepository;
    private final BodegaRepository bodegaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final MovimientoMateriaPrimaRepository movimientoMateriaPrimaRepository;
    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final LogService logService;

    @PersistenceContext
    private EntityManager entityManager;

    public OrdenProduccionService(OrdenProduccionRepository ordenRepository,
                                  OrdenProduccionConsumoRepository consumoRepository,
                                  ListaMaterialesRepository listaMaterialesRepository,
                                  ProductoRepository productoRepository,
                                  BodegaRepository bodegaRepository,
                                  UsuarioRepository usuarioRepository,
                                  MateriaPrimaRepository materiaPrimaRepository,
                                  MovimientoMateriaPrimaRepository movimientoMateriaPrimaRepository,
                                  InventarioRepository inventarioRepository,
                                  MovimientoInventarioRepository movimientoInventarioRepository,
                                  LogService logService) {
        this.ordenRepository = ordenRepository;
        this.consumoRepository = consumoRepository;
        this.listaMaterialesRepository = listaMaterialesRepository;
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.usuarioRepository = usuarioRepository;
        this.materiaPrimaRepository = materiaPrimaRepository;
        this.movimientoMateriaPrimaRepository = movimientoMateriaPrimaRepository;
        this.inventarioRepository = inventarioRepository;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
        this.logService = logService;
    }

    // =================================================================
    // Verificación de disponibilidad (chequeo previo, no consume nada)
    // =================================================================
    public VerificacionDisponibilidadDTO verificarDisponibilidad(Integer idProducto, Integer cantidad) {
        if (cantidad == null || cantidad < 1) {
            throw new ValidationException("La cantidad a producir debe ser al menos 1");
        }
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", idProducto));

        List<ListaMateriales> bom = listaMaterialesRepository
                .findByProductoIdProductoAndEstado(idProducto, "activo");
        if (bom.isEmpty()) {
            throw new ValidationException(
                "El producto no tiene lista de materiales definida. Configure el BOM antes de producir.");
        }

        BigDecimal cant = BigDecimal.valueOf(cantidad);
        List<DisponibilidadMaterialDTO> materiales = new ArrayList<>();
        boolean puede = true;
        Integer maxProducible = null;

        for (ListaMateriales linea : bom) {
            MateriaPrima mp = linea.getMateriaPrima();
            BigDecimal necesariaUnitaria = linea.getCantidadNecesaria();
            BigDecimal necesariaTotal = necesariaUnitaria.multiply(cant);
            BigDecimal stock = mp.getStockActual() != null ? mp.getStockActual() : BigDecimal.ZERO;

            boolean suficiente = stock.compareTo(necesariaTotal) >= 0;
            BigDecimal faltante = necesariaTotal.subtract(stock);
            if (faltante.compareTo(BigDecimal.ZERO) < 0) faltante = BigDecimal.ZERO;
            if (!suficiente) puede = false;

            // cuántas unidades alcanzan con este material: floor(stock / necesariaUnitaria)
            int maxParaEste = stock.divideToIntegralValue(necesariaUnitaria).intValue();
            if (maxProducible == null || maxParaEste < maxProducible) {
                maxProducible = maxParaEste;
            }

            DisponibilidadMaterialDTO d = new DisponibilidadMaterialDTO();
            d.setIdMateriaPrima(mp.getIdMateriaPrima());
            d.setNombreMateriaPrima(mp.getNombre());
            d.setUnidadMedida(mp.getUnidadMedida() != null ? mp.getUnidadMedida().getNombre() : null);
            d.setCantidadNecesaria(necesariaTotal);
            d.setStockDisponible(stock);
            d.setSuficiente(suficiente);
            d.setFaltante(faltante);
            materiales.add(d);
        }

        VerificacionDisponibilidadDTO dto = new VerificacionDisponibilidadDTO();
        dto.setPuedeProducir(puede);
        dto.setMateriales(materiales);
        dto.setCantidadMaximaProducible(maxProducible != null ? maxProducible : 0);
        return dto;
    }

    // =================================================================
    // Crear (estado planificada) — no consume materia prima
    // =================================================================
    @Transactional
    public OrdenProduccionResponseDTO crear(OrdenProduccionRequestDTO dto, Integer idUsuarioActual) {
        Producto producto = productoRepository.findById(dto.getIdProducto())
                .orElseThrow(() -> new ResourceNotFoundException("Producto", dto.getIdProducto()));
        if (!"fabricado".equals(producto.getOrigen())) {
            throw new ValidationException(
                "Solo productos con origen=fabricado pueden producirse (producto: " + producto.getNombre() + ")");
        }
        Bodega bodega = bodegaRepository.findById(dto.getIdBodegaDestino())
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", dto.getIdBodegaDestino()));
        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        VerificacionDisponibilidadDTO verif = verificarDisponibilidad(dto.getIdProducto(), dto.getCantidadPlanificada());
        if (!Boolean.TRUE.equals(verif.getPuedeProducir())) {
            throw new ValidationException(mensajeFaltantes(verif));
        }

        OrdenProduccion orden = new OrdenProduccion();
        orden.setProducto(producto);
        orden.setBodegaDestino(bodega);
        orden.setUsuarioRegistro(usuario);
        orden.setCantidadPlanificada(dto.getCantidadPlanificada());
        orden.setEstado("planificada");
        orden.setObservaciones(dto.getObservaciones());
        orden = ordenRepository.save(orden);

        BigDecimal cant = BigDecimal.valueOf(dto.getCantidadPlanificada());
        List<ListaMateriales> bom = listaMaterialesRepository
                .findByProductoIdProductoAndEstado(dto.getIdProducto(), "activo");
        for (ListaMateriales linea : bom) {
            OrdenProduccionConsumo c = new OrdenProduccionConsumo();
            c.setOrdenProduccion(orden);
            c.setMateriaPrima(linea.getMateriaPrima());
            c.setCantidadTeorica(linea.getCantidadNecesaria().multiply(cant));
            c.setCantidadReal(null);
            consumoRepository.save(c);
        }

        logService.registrar(idUsuarioActual, "produccion", "crear",
                "OP #" + orden.getIdOrdenProduccion() + " planificada: " + dto.getCantidadPlanificada()
                        + " x " + producto.getNombre(), null);

        return obtener(orden.getIdOrdenProduccion());
    }

    // =================================================================
    // Iniciar (estado en_proceso) — CONSUME materia prima
    // =================================================================
    @Transactional
    public OrdenProduccionResponseDTO iniciar(Integer id, Integer idUsuarioActual) {
        OrdenProduccion orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de producción", id));
        if (!"planificada".equals(orden.getEstado())) {
            throw new ValidationException("Solo se puede iniciar una orden en estado 'planificada' (estado actual: "
                    + orden.getEstado() + ")");
        }
        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        // Re-verificar disponibilidad: el stock pudo cambiar desde la planificación
        VerificacionDisponibilidadDTO verif = verificarDisponibilidad(
                orden.getProducto().getIdProducto(), orden.getCantidadPlanificada());
        if (!Boolean.TRUE.equals(verif.getPuedeProducir())) {
            throw new ValidationException("No se puede iniciar: " + mensajeFaltantes(verif));
        }

        List<OrdenProduccionConsumo> consumos = consumoRepository.findByOrdenProduccionIdOrdenProduccion(id);
        for (OrdenProduccionConsumo c : consumos) {
            MateriaPrima mp = c.getMateriaPrima();
            BigDecimal stockAnterior = mp.getStockActual();
            BigDecimal stockNuevo = stockAnterior.subtract(c.getCantidadTeorica());
            if (stockNuevo.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Stock insuficiente de " + mp.getNombre()
                        + " al iniciar (disponible: " + stockAnterior + ")");
            }
            mp.setStockActual(stockNuevo);
            materiaPrimaRepository.save(mp);

            registrarMovimientoMp(mp, usuario, "salida_produccion", c.getCantidadTeorica(),
                    stockAnterior, stockNuevo, orden.getIdOrdenProduccion(),
                    "Consumo OP #" + orden.getIdOrdenProduccion() + " - " + orden.getProducto().getNombre());
        }

        orden.setEstado("en_proceso");
        orden.setFechaInicio(java.time.LocalDateTime.now());
        ordenRepository.save(orden);

        logService.registrar(idUsuarioActual, "produccion", "iniciar",
                "OP #" + orden.getIdOrdenProduccion() + " iniciada: materia prima consumida", null);

        return obtener(id);
    }

    // =================================================================
    // Completar (estado completada) — declara producidas, ajusta mermas,
    // da de alta el producto terminado en inventario
    // =================================================================
    @Transactional
    public OrdenProduccionResponseDTO completar(Integer id, CompletarProduccionDTO dto, Integer idUsuarioActual) {
        OrdenProduccion orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de producción", id));
        if (!"en_proceso".equals(orden.getEstado())) {
            throw new ValidationException("Solo se puede completar una orden en estado 'en_proceso' (estado actual: "
                    + orden.getEstado() + ")");
        }
        if (dto.getCantidadProducida() == null || dto.getCantidadProducida() < 0) {
            throw new ValidationException("La cantidad producida es obligatoria y no puede ser negativa");
        }
        if (dto.getCantidadProducida() > orden.getCantidadPlanificada()) {
            throw new ValidationException("La cantidad producida no puede superar la planificada (planificado: "
                    + orden.getCantidadPlanificada() + ")");
        }
        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        List<OrdenProduccionConsumo> consumos = consumoRepository.findByOrdenProduccionIdOrdenProduccion(id);

        // Fija el usuario para el trigger de historial de inventario (una vez por transacción)
        entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + idUsuarioActual + "'")
                .executeUpdate();

        if (dto.getConsumosReales() != null && !dto.getConsumosReales().isEmpty()) {
            Map<Integer, BigDecimal> reales = new HashMap<>();
            for (ConsumoRealItemDTO item : dto.getConsumosReales()) {
                reales.put(item.getIdMateriaPrima(), item.getCantidadReal());
            }
            for (OrdenProduccionConsumo c : consumos) {
                BigDecimal real = reales.get(c.getMateriaPrima().getIdMateriaPrima());
                if (real == null) continue; // no declarado → queda teórico (merma 0)
                c.setCantidadReal(real);
                BigDecimal diff = real.subtract(c.getCantidadTeorica()); // + usó de más, - sobró

                MateriaPrima mp = c.getMateriaPrima();
                if (diff.compareTo(BigDecimal.ZERO) > 0) {
                    // Se usó MÁS de lo previsto: descontar la diferencia del stock (merma)
                    BigDecimal stockAnterior = mp.getStockActual();
                    BigDecimal stockNuevo = stockAnterior.subtract(diff);
                    if (stockNuevo.compareTo(BigDecimal.ZERO) < 0) {
                        throw new ValidationException("Stock insuficiente para cubrir el consumo real extra de "
                                + mp.getNombre() + " (disponible: " + stockAnterior + ", requerido adicional: " + diff + ")");
                    }
                    mp.setStockActual(stockNuevo);
                    materiaPrimaRepository.save(mp);
                    registrarMovimientoMp(mp, usuario, "merma", diff, stockAnterior, stockNuevo,
                            orden.getIdOrdenProduccion(),
                            "Merma OP #" + orden.getIdOrdenProduccion() + " - " + mp.getNombre());
                } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                    // Sobró material: devolver la diferencia al stock
                    BigDecimal devolver = diff.abs();
                    BigDecimal stockAnterior = mp.getStockActual();
                    BigDecimal stockNuevo = stockAnterior.add(devolver);
                    mp.setStockActual(stockNuevo);
                    materiaPrimaRepository.save(mp);
                    registrarMovimientoMp(mp, usuario, "ajuste", devolver, stockAnterior, stockNuevo,
                            orden.getIdOrdenProduccion(),
                            "Devolución de sobrante OP #" + orden.getIdOrdenProduccion());
                }
                consumoRepository.save(c);
            }
        } else {
            // Sin consumo real declarado: real = teórico, merma 0, sin ajustes
            for (OrdenProduccionConsumo c : consumos) {
                c.setCantidadReal(c.getCantidadTeorica());
                consumoRepository.save(c);
            }
        }

        // Dar de alta el producto terminado en inventario
        if (dto.getCantidadProducida() > 0) {
            darDeAltaProductoTerminado(orden, dto.getCantidadProducida(), usuario, idUsuarioActual);
        }

        orden.setEstado("completada");
        orden.setCantidadProducida(dto.getCantidadProducida());
        orden.setFechaFin(java.time.LocalDateTime.now());
        orden.setUsuarioCompleta(usuario);
        if (dto.getObservaciones() != null && !dto.getObservaciones().isBlank()) {
            orden.setObservaciones(dto.getObservaciones());
        }
        ordenRepository.save(orden);

        logService.registrar(idUsuarioActual, "produccion", "completar",
                "OP #" + orden.getIdOrdenProduccion() + " completada: " + dto.getCantidadProducida()
                        + " unidades de " + orden.getProducto().getNombre(), null);

        return obtener(id);
    }

    private void darDeAltaProductoTerminado(OrdenProduccion orden, int cantidad, Usuario usuario, Integer idUsuarioActual) {
        Producto producto = orden.getProducto();
        Bodega bodega = orden.getBodegaDestino();

        Inventario inv = inventarioRepository
                .findByProductoIdProductoAndBodegaIdBodega(producto.getIdProducto(), bodega.getIdBodega())
                .orElseGet(() -> {
                    Inventario nuevo = new Inventario();
                    nuevo.setProducto(producto);
                    nuevo.setBodega(bodega);
                    nuevo.setStockActual(0);
                    nuevo.setStockMinimo(0);
                    return inventarioRepository.save(nuevo);
                });

        // Reafirma el usuario para el trigger de historial de inventario
        entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + idUsuarioActual + "'")
                .executeUpdate();
        inv.setStockActual(inv.getStockActual() + cantidad);
        inventarioRepository.save(inv);

        MovimientoInventario mov = new MovimientoInventario();
        mov.setInventario(inv);
        mov.setTipoMovimiento("entrada");
        mov.setCantidad(cantidad);
        mov.setUsuario(usuario);
        mov.setObservacion("Producción OP #" + orden.getIdOrdenProduccion());
        movimientoInventarioRepository.save(mov);
    }

    // =================================================================
    // Cancelar (solo planificada)
    // =================================================================
    @Transactional
    public OrdenProduccionResponseDTO cancelar(Integer id, Integer idUsuarioActual) {
        OrdenProduccion orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de producción", id));
        if ("en_proceso".equals(orden.getEstado())) {
            throw new ValidationException("No se puede cancelar una orden en proceso: la materia prima ya fue consumida. "
                    + "Complétela indicando la cantidad realmente producida.");
        }
        if (!"planificada".equals(orden.getEstado())) {
            throw new ValidationException("Solo se puede cancelar una orden en estado 'planificada' (estado actual: "
                    + orden.getEstado() + ")");
        }
        orden.setEstado("cancelada");
        ordenRepository.save(orden);

        logService.registrar(idUsuarioActual, "produccion", "cancelar",
                "OP #" + orden.getIdOrdenProduccion() + " cancelada", null);

        return obtener(id);
    }

    // =================================================================
    // Consultas
    // =================================================================
    public PageResponseDTO<OrdenProduccionResponseDTO> listar(int page, int size, String estado, Integer idProducto) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "idOrdenProduccion"));
        Page<OrdenProduccion> result = ordenRepository.buscar(
                (estado != null && !estado.isEmpty()) ? estado : null, idProducto, pageable);
        List<OrdenProduccionResponseDTO> content = result.getContent().stream()
                .map(o -> toDTO(o, false))
                .collect(Collectors.toList());
        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public OrdenProduccionResponseDTO obtener(Integer id) {
        OrdenProduccion orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de producción", id));
        return toDTO(orden, true);
    }

    // =================================================================
    // Helpers
    // =================================================================
    private String mensajeFaltantes(VerificacionDisponibilidadDTO verif) {
        String detalle = verif.getMateriales().stream()
                .filter(m -> Boolean.FALSE.equals(m.getSuficiente()))
                .map(m -> m.getNombreMateriaPrima() + " (faltan " + m.getFaltante()
                        + (m.getUnidadMedida() != null ? " " + m.getUnidadMedida() : "") + ")")
                .collect(Collectors.joining("; "));
        return "Materia prima insuficiente: " + detalle;
    }

    private void registrarMovimientoMp(MateriaPrima mp, Usuario usuario, String tipo, BigDecimal cantidad,
                                       BigDecimal stockAnterior, BigDecimal stockNuevo,
                                       Integer idOrdenProduccion, String observacion) {
        if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) return; // CHECK cantidad > 0
        MovimientoMateriaPrima mov = new MovimientoMateriaPrima();
        mov.setMateriaPrima(mp);
        mov.setUsuario(usuario);
        mov.setTipoMovimiento(tipo);
        mov.setCantidad(cantidad);
        mov.setStockAnterior(stockAnterior);
        mov.setStockNuevo(stockNuevo);
        mov.setIdOrdenProduccion(idOrdenProduccion);
        mov.setObservacion(observacion);
        movimientoMateriaPrimaRepository.save(mov);
    }

    private OrdenProduccionResponseDTO toDTO(OrdenProduccion o, boolean incluirConsumos) {
        OrdenProduccionResponseDTO dto = new OrdenProduccionResponseDTO();
        dto.setIdOrdenProduccion(o.getIdOrdenProduccion());
        dto.setCantidadPlanificada(o.getCantidadPlanificada());
        dto.setCantidadProducida(o.getCantidadProducida());
        dto.setEstado(o.getEstado());
        dto.setFechaCreacion(o.getFechaCreacion());
        dto.setFechaInicio(o.getFechaInicio());
        dto.setFechaFin(o.getFechaFin());
        dto.setObservaciones(o.getObservaciones());
        if (o.getProducto() != null) {
            dto.setIdProducto(o.getProducto().getIdProducto());
            dto.setProductoNombre(o.getProducto().getNombre());
        }
        if (o.getBodegaDestino() != null) {
            dto.setIdBodegaDestino(o.getBodegaDestino().getIdBodega());
            dto.setBodegaNombre(o.getBodegaDestino().getNombre());
        }
        if (o.getUsuarioRegistro() != null) {
            dto.setIdUsuarioRegistro(o.getUsuarioRegistro().getIdUsuario());
            dto.setUsuarioRegistroNombre(o.getUsuarioRegistro().getNombre() + " " + o.getUsuarioRegistro().getApellido());
        }
        if (o.getUsuarioCompleta() != null) {
            dto.setIdUsuarioCompleta(o.getUsuarioCompleta().getIdUsuario());
            dto.setUsuarioCompletaNombre(o.getUsuarioCompleta().getNombre() + " " + o.getUsuarioCompleta().getApellido());
        }
        if (incluirConsumos) {
            List<OrdenProduccionConsumo> consumos = consumoRepository.findByOrdenProduccionIdOrdenProduccion(o.getIdOrdenProduccion());
            List<OrdenProduccionResponseDTO.ConsumoDTO> lista = new ArrayList<>();
            for (OrdenProduccionConsumo c : consumos) {
                OrdenProduccionResponseDTO.ConsumoDTO cd = new OrdenProduccionResponseDTO.ConsumoDTO();
                cd.setIdConsumo(c.getIdConsumo());
                cd.setCantidadTeorica(c.getCantidadTeorica());
                cd.setCantidadReal(c.getCantidadReal());
                cd.setMerma(c.getMerma());
                if (c.getMateriaPrima() != null) {
                    cd.setIdMateriaPrima(c.getMateriaPrima().getIdMateriaPrima());
                    cd.setMateriaPrimaNombre(c.getMateriaPrima().getNombre());
                    cd.setUnidadMedida(c.getMateriaPrima().getUnidadMedida() != null
                            ? c.getMateriaPrima().getUnidadMedida().getNombre() : null);
                }
                lista.add(cd);
            }
            dto.setConsumos(lista);
        }
        return dto;
    }
}
