package com.marathon.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.inventario.HistorialResponseDTO;
import com.marathon.dto.inventario.InventarioResponseDTO;
import com.marathon.dto.inventario.MovimientoRequestDTO;
import com.marathon.dto.inventario.MovimientoResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.Bodega;
import com.marathon.model.HistorialInventario;
import com.marathon.model.Inventario;
import com.marathon.model.MovimientoInventario;
import com.marathon.model.Producto;
import com.marathon.model.Usuario;
import com.marathon.repository.BodegaRepository;
import com.marathon.repository.HistorialInventarioRepository;
import com.marathon.repository.InventarioRepository;
import com.marathon.repository.MovimientoInventarioRepository;
import com.marathon.repository.ProductoRepository;
import com.marathon.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class InventarioService {

    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final HistorialInventarioRepository historialRepository;
    private final ProductoRepository productoRepository;
    private final BodegaRepository bodegaRepository;
    private final UsuarioRepository usuarioRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public InventarioService(InventarioRepository inventarioRepository,
                             MovimientoInventarioRepository movimientoRepository,
                             HistorialInventarioRepository historialRepository,
                             ProductoRepository productoRepository,
                             BodegaRepository bodegaRepository,
                             UsuarioRepository usuarioRepository) {
        this.inventarioRepository = inventarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.historialRepository = historialRepository;
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public PageResponseDTO<InventarioResponseDTO> listar(int page, int size, Integer idBodega) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Inventario> result;

        if (idBodega != null) {
            result = inventarioRepository.findByBodegaIdBodega(idBodega, pageable);
        } else {
            result = inventarioRepository.findAll(pageable);
        }

        List<InventarioResponseDTO> content = result.getContent().stream()
                .map(this::toInventarioDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public InventarioResponseDTO obtener(Integer id) {
        Inventario inventario = inventarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventario", id));
        return toInventarioDTO(inventario);
    }

    public List<InventarioResponseDTO> stockBajo() {
        return inventarioRepository.findStockBajo(5).stream()
                .map(this::toInventarioDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public MovimientoResponseDTO registrarMovimiento(MovimientoRequestDTO dto, Integer idUsuarioActual) {
        Producto producto = productoRepository.findById(dto.getIdProducto())
                .orElseThrow(() -> new ResourceNotFoundException("Producto", dto.getIdProducto()));
        Bodega bodega = bodegaRepository.findById(dto.getIdBodega())
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", dto.getIdBodega()));
        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        Inventario inv = inventarioRepository.findByProductoIdProductoAndBodegaIdBodega(
                dto.getIdProducto(), dto.getIdBodega())
                .orElseGet(() -> {
                    Inventario nuevo = new Inventario();
                    nuevo.setProducto(producto);
                    nuevo.setBodega(bodega);
                    nuevo.setCantidad(0);
                    return inventarioRepository.save(nuevo);
                });

        int stockAnterior = inv.getCantidad();

        switch (dto.getTipoMovimiento()) {
            case "entrada":
                inv.setCantidad(inv.getCantidad() + dto.getCantidad());
                break;
            case "salida":
                if (inv.getCantidad() < dto.getCantidad()) {
                    throw new ValidationException("Stock insuficiente. Disponible: " + inv.getCantidad());
                }
                inv.setCantidad(inv.getCantidad() - dto.getCantidad());
                break;
            case "ajuste":
                inv.setCantidad(dto.getCantidad());
                break;
            case "traslado":
                if (dto.getIdBodegaDestino() == null) {
                    throw new ValidationException("La bodega destino es requerida para traslados");
                }
                if (inv.getCantidad() < dto.getCantidad()) {
                    throw new ValidationException("Stock insuficiente. Disponible: " + inv.getCantidad());
                }
                inv.setCantidad(inv.getCantidad() - dto.getCantidad());

                Bodega bodegaDestino = bodegaRepository.findById(dto.getIdBodegaDestino())
                        .orElseThrow(() -> new ResourceNotFoundException("Bodega destino", dto.getIdBodegaDestino()));

                Inventario destino = inventarioRepository.findByProductoIdProductoAndBodegaIdBodega(
                        dto.getIdProducto(), dto.getIdBodegaDestino())
                        .orElseGet(() -> {
                            Inventario nuevoDestino = new Inventario();
                            nuevoDestino.setProducto(producto);
                            nuevoDestino.setBodega(bodegaDestino);
                            nuevoDestino.setCantidad(0);
                            return inventarioRepository.save(nuevoDestino);
                        });

                entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + idUsuarioActual + "'")
                        .executeUpdate();
                destino.setCantidad(destino.getCantidad() + dto.getCantidad());
                inventarioRepository.save(destino);
                break;
            default:
                throw new ValidationException("Tipo de movimiento no válido: " + dto.getTipoMovimiento());
        }

        // SET LOCAL before saving origin inventory
        entityManager.createNativeQuery("SET LOCAL app.current_user_id = '" + idUsuarioActual + "'")
                .executeUpdate();
        inventarioRepository.save(inv);

        // Save movimiento record
        MovimientoInventario mov = new MovimientoInventario();
        mov.setProducto(producto);
        mov.setBodega(bodega);
        mov.setTipoMovimiento(dto.getTipoMovimiento());
        mov.setCantidad(dto.getCantidad());
        mov.setUsuario(usuario);
        movimientoRepository.save(mov);

        return toMovimientoDTO(mov);
    }

    public PageResponseDTO<MovimientoResponseDTO> listarMovimientos(Integer idProducto, Integer idBodega, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MovimientoInventario> result = movimientoRepository
                .findByProductoIdProductoAndBodegaIdBodega(idProducto, idBodega, pageable);

        List<MovimientoResponseDTO> content = result.getContent().stream()
                .map(this::toMovimientoDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public List<HistorialResponseDTO> listarHistorial(Integer idInventario) {
        return historialRepository.findByInventarioIdInventarioOrderByFechaCambioDesc(idInventario).stream()
                .map(this::toHistorialDTO)
                .collect(Collectors.toList());
    }

    private InventarioResponseDTO toInventarioDTO(Inventario inv) {
        InventarioResponseDTO dto = new InventarioResponseDTO();
        dto.setIdInventario(inv.getIdInventario());
        dto.setCantidad(inv.getCantidad());
        dto.setUpdatedAt(inv.getUpdatedAt());
        if (inv.getProducto() != null) {
            dto.setProductoId(inv.getProducto().getIdProducto());
            dto.setProductoNombre(inv.getProducto().getNombre());
        }
        if (inv.getBodega() != null) {
            dto.setBodegaId(inv.getBodega().getIdBodega());
            dto.setBodegaNombre(inv.getBodega().getNombre());
        }
        return dto;
    }

    private MovimientoResponseDTO toMovimientoDTO(MovimientoInventario mov) {
        MovimientoResponseDTO dto = new MovimientoResponseDTO();
        dto.setIdMovimiento(mov.getIdMovimiento());
        dto.setTipoMovimiento(mov.getTipoMovimiento());
        dto.setCantidad(mov.getCantidad());
        dto.setFecha(mov.getFecha());
        if (mov.getProducto() != null) {
            dto.setIdProducto(mov.getProducto().getIdProducto());
            dto.setProductoNombre(mov.getProducto().getNombre());
        }
        if (mov.getBodega() != null) {
            dto.setIdBodega(mov.getBodega().getIdBodega());
            dto.setBodegaNombre(mov.getBodega().getNombre());
        }
        if (mov.getUsuario() != null) {
            dto.setIdUsuario(mov.getUsuario().getIdUsuario());
            dto.setUsuarioNombre(mov.getUsuario().getNombre() + " " + mov.getUsuario().getApellido());
        }
        return dto;
    }

    private HistorialResponseDTO toHistorialDTO(HistorialInventario h) {
        HistorialResponseDTO dto = new HistorialResponseDTO();
        dto.setIdHistorial(h.getIdHistorial());
        dto.setCantidadAnterior(h.getCantidadAnterior());
        dto.setCantidadNueva(h.getCantidadNueva());
        dto.setFechaCambio(h.getFechaCambio());
        dto.setTipoOperacion(h.getTipoOperacion());
        return dto;
    }
}
