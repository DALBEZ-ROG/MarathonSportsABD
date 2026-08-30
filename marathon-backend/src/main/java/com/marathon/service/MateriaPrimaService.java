package com.marathon.service;

import java.util.List;
import java.math.BigDecimal;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.materiaprima.MateriaPrimaRequestDTO;
import com.marathon.dto.materiaprima.MateriaPrimaResponseDTO;
import com.marathon.dto.materiaprima.MovimientoMateriaPrimaRequestDTO;
import com.marathon.dto.materiaprima.MovimientoMateriaPrimaResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.MateriaPrima;
import com.marathon.model.MovimientoMateriaPrima;
import com.marathon.model.UnidadMedida;
import com.marathon.model.Usuario;
import com.marathon.repository.MateriaPrimaRepository;
import com.marathon.repository.MovimientoMateriaPrimaRepository;
import com.marathon.repository.UnidadMedidaRepository;
import com.marathon.repository.UsuarioRepository;

@Service
public class MateriaPrimaService {

    private final MateriaPrimaRepository materiaPrimaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final MovimientoMateriaPrimaRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;

    private final LogService logService;

    public MateriaPrimaService(MateriaPrimaRepository materiaPrimaRepository,
                               UnidadMedidaRepository unidadMedidaRepository,
                               MovimientoMateriaPrimaRepository movimientoRepository,
                               UsuarioRepository usuarioRepository,
                           LogService logService) {
        this.materiaPrimaRepository = materiaPrimaRepository;
        this.unidadMedidaRepository = unidadMedidaRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.logService = logService;
    }

    public PageResponseDTO<MateriaPrimaResponseDTO> listar(int page, int size, String nombre, String estado) {
        // F51 (D-41): el ORDEN es obligatorio en una lista paginada.
        // Sin ORDER BY, PostgreSQL devuelve las filas en el orden del monton, y
        // un UPDATE reescribe la fila al final: la que acabas de editar
        // desaparece de su pagina. Ademas, dos paginas consecutivas pueden
        // repetir una fila y esconder otra.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "idMateriaPrima"));
        Page<MateriaPrima> result;

        boolean hasNombre = nombre != null && !nombre.isEmpty();
        boolean hasEstado = estado != null && !estado.isEmpty();

        if (hasNombre && hasEstado) {
            result = materiaPrimaRepository.findByNombreContainingIgnoreCaseAndEstado(nombre, estado, pageable);
        } else if (hasNombre) {
            result = materiaPrimaRepository.findByNombreContainingIgnoreCase(nombre, pageable);
        } else if (hasEstado) {
            result = materiaPrimaRepository.findByEstado(estado, pageable);
        } else {
            result = materiaPrimaRepository.findAll(pageable);
        }

        List<MateriaPrimaResponseDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public MateriaPrimaResponseDTO obtener(Integer id) {
        MateriaPrima mp = materiaPrimaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Materia prima", id));
        return toDTO(mp);
    }

    @Transactional
    public MateriaPrimaResponseDTO crear(MateriaPrimaRequestDTO dto) {
        materiaPrimaRepository.findByNombreIgnoreCase(dto.getNombre()).ifPresent(m -> {
            throw new ValidationException("Ya existe una materia prima con el nombre: " + dto.getNombre());
        });

        MateriaPrima mp = new MateriaPrima();
        mapFromDTO(mp, dto);
        mp.setEstado(dto.getEstado() != null ? dto.getEstado() : "activo");
        mp = materiaPrimaRepository.save(mp);

        logService.registrarAccion("materia-prima", "crear",
                "Materia prima #" + mp.getIdMateriaPrima() + " '" + mp.getNombre() + "' creada");

        return toDTO(mp);
    }

    @Transactional
    public MateriaPrimaResponseDTO actualizar(Integer id, MateriaPrimaRequestDTO dto) {
        MateriaPrima mp = materiaPrimaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Materia prima", id));

        materiaPrimaRepository.findByNombreIgnoreCase(dto.getNombre()).ifPresent(existente -> {
            if (!existente.getIdMateriaPrima().equals(id)) {
                throw new ValidationException("Ya existe una materia prima con el nombre: " + dto.getNombre());
            }
        });

        mapFromDTO(mp, dto);
        if (dto.getEstado() != null) {
            mp.setEstado(dto.getEstado());
        }
        mp = materiaPrimaRepository.save(mp);

        logService.registrarAccion("materia-prima", "actualizar",
                "Materia prima #" + id + " '" + mp.getNombre() + "' modificada");

        return toDTO(mp);
    }

    @Transactional
    public void eliminar(Integer id) {
        MateriaPrima mp = materiaPrimaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Materia prima", id));
        mp.setEstado("inactivo");
        materiaPrimaRepository.save(mp);

        logService.registrarAccion("materia-prima", "eliminar",
                "Materia prima #" + id + " '" + mp.getNombre() + "' dada de baja");
    }

    // ---- F26: Kardex de movimientos ----

    @Transactional
    public MovimientoMateriaPrimaResponseDTO registrarMovimientoManual(MovimientoMateriaPrimaRequestDTO dto, Integer idUsuarioActual) {
        MateriaPrima mp = materiaPrimaRepository.findById(dto.getIdMateriaPrima())
                .orElseThrow(() -> new ResourceNotFoundException("Materia prima", dto.getIdMateriaPrima()));
        Usuario usuario = usuarioRepository.findById(idUsuarioActual)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", idUsuarioActual));

        BigDecimal stockAnterior = mp.getStockActual();
        BigDecimal stockNuevo;

        if ("ajuste".equals(dto.getTipoMovimiento())) {
            if (Boolean.TRUE.equals(dto.getEsIncremento())) {
                stockNuevo = stockAnterior.add(dto.getCantidad());
            } else {
                stockNuevo = stockAnterior.subtract(dto.getCantidad());
            }
        } else { // merma — siempre resta
            stockNuevo = stockAnterior.subtract(dto.getCantidad());
        }

        if (stockNuevo.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("El movimiento dejaria el stock en negativo (disponible: " + stockAnterior + ")");
        }

        mp.setStockActual(stockNuevo);
        materiaPrimaRepository.save(mp);

        MovimientoMateriaPrima mov = new MovimientoMateriaPrima();
        mov.setMateriaPrima(mp);
        mov.setUsuario(usuario);
        mov.setTipoMovimiento(dto.getTipoMovimiento());
        mov.setCantidad(dto.getCantidad());
        mov.setStockAnterior(stockAnterior);
        mov.setStockNuevo(stockNuevo);
        mov.setObservacion(dto.getObservacion());
        mov = movimientoRepository.save(mov);

        return toMovDTO(mov);
    }

    public PageResponseDTO<MovimientoMateriaPrimaResponseDTO> listarMovimientos(int page, int size, Integer idMateriaPrima) {
        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "idMovimientoMp"));
        Page<MovimientoMateriaPrima> result = movimientoRepository.findByMateriaPrimaIdMateriaPrima(idMateriaPrima, pageable);
        List<MovimientoMateriaPrimaResponseDTO> content = result.getContent().stream()
                .map(this::toMovDTO).collect(Collectors.toList());
        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    /**
     * Cuántas materias primas hay bajo mínimos. Solo el número.
     *
     * <p>Es lo que necesita un aviso, y cuesta una consulta contra un índice
     * parcial en lugar de traerse las filas.
     */
    public long contarStockBajo() {
        return materiaPrimaRepository.contarBajoMinimo();
    }

    /**
     * Las materias primas bajo mínimos, en orden y ACOTADAS.
     *
     * <p>F94: el filtro se hace en la base, no en Java. Lo anterior era
     * {@code findAll()} + {@code .filter()}, que con 1,5 millones de filas es
     * traerse la tabla entera al servidor —10 segundos— para quedarse con unas
     * pocas.
     *
     * <p>El tope existe porque esto es una LISTA DE TRABAJO: quien la abre va a
     * reponer, y nadie repone diez mil referencias de una sentada. Devolver
     * todas era además lo que hacía que la pantalla tardara en pintar. El total
     * real se pide aparte con {@link #contarStockBajo()}, así que el número del
     * aviso sigue siendo exacto aunque la lista esté recortada.
     */
    public List<MateriaPrimaResponseDTO> listarStockBajo(int limite) {
        int tope = Math.min(Math.max(limite, 1), 500);
        return materiaPrimaRepository
                .findBajoMinimo(org.springframework.data.domain.PageRequest.of(0, tope)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public MateriaPrimaResponseDTO actualizarStockMinimo(Integer id, BigDecimal nuevoMinimo) {
        MateriaPrima mp = materiaPrimaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Materia prima", id));
        mp.setStockMinimo(nuevoMinimo);
        materiaPrimaRepository.save(mp);
        return toDTO(mp);
    }

    private MovimientoMateriaPrimaResponseDTO toMovDTO(MovimientoMateriaPrima mov) {
        MovimientoMateriaPrimaResponseDTO dto = new MovimientoMateriaPrimaResponseDTO();
        dto.setIdMovimientoMp(mov.getIdMovimientoMp());
        dto.setTipoMovimiento(mov.getTipoMovimiento());
        dto.setCantidad(mov.getCantidad());
        dto.setStockAnterior(mov.getStockAnterior());
        dto.setStockNuevo(mov.getStockNuevo());
        dto.setIdRecepcion(mov.getIdRecepcion());
        dto.setIdOrdenProduccion(mov.getIdOrdenProduccion());
        dto.setObservacion(mov.getObservacion());
        dto.setFecha(mov.getFecha());
        if (mov.getMateriaPrima() != null) {
            dto.setIdMateriaPrima(mov.getMateriaPrima().getIdMateriaPrima());
            dto.setMateriaPrimaNombre(mov.getMateriaPrima().getNombre());
        }
        if (mov.getUsuario() != null) {
            dto.setUsuarioNombre(mov.getUsuario().getNombre() + " " + mov.getUsuario().getApellido());
        }
        return dto;
    }

    private void mapFromDTO(MateriaPrima mp, MateriaPrimaRequestDTO dto) {
        mp.setNombre(dto.getNombre());
        mp.setDescripcion(dto.getDescripcion());

        UnidadMedida unidad = unidadMedidaRepository.findById(dto.getIdUnidadMedida())
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", dto.getIdUnidadMedida()));
        mp.setUnidadMedida(unidad);
    }

    private MateriaPrimaResponseDTO toDTO(MateriaPrima mp) {
        MateriaPrimaResponseDTO dto = new MateriaPrimaResponseDTO();
        dto.setIdMateriaPrima(mp.getIdMateriaPrima());
        dto.setNombre(mp.getNombre());
        dto.setDescripcion(mp.getDescripcion());
        dto.setEstado(mp.getEstado());
        dto.setStockActual(mp.getStockActual());
        dto.setStockMinimo(mp.getStockMinimo());
        dto.setCostoUnitarioPromedio(mp.getCostoUnitarioPromedio());
        dto.setStockBajo(mp.getStockMinimo() != null && mp.getStockMinimo().compareTo(java.math.BigDecimal.ZERO) > 0
                && mp.getStockActual().compareTo(mp.getStockMinimo()) <= 0);
        dto.setCreatedAt(mp.getCreatedAt());
        if (mp.getUnidadMedida() != null) {
            dto.setIdUnidadMedida(mp.getUnidadMedida().getIdUnidadMedida());
            dto.setUnidadMedidaNombre(mp.getUnidadMedida().getNombre());
        }
        return dto;
    }
}
