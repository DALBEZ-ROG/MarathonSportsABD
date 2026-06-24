package com.marathon.service;

import com.marathon.dto.PageResponseDTO;
import com.marathon.dto.auditoria.AuditoriaHistorialDTO;
import com.marathon.dto.auditoria.ResumenHistorialDTO;
import com.marathon.model.HistorialInventario;
import com.marathon.model.Inventario;
import com.marathon.model.Usuario;
import com.marathon.repository.HistorialInventarioRepository;
import com.marathon.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditoriaService {

    private final HistorialInventarioRepository historialRepository;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaService(HistorialInventarioRepository historialRepository,
                            UsuarioRepository usuarioRepository) {
        this.historialRepository = historialRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public PageResponseDTO<AuditoriaHistorialDTO> listarHistorial(int page, int size, Integer idProducto,
                                                                  Integer idBodega, LocalDateTime desde,
                                                                  LocalDateTime hasta) {
        Integer prod = idProducto != null ? idProducto : 0;
        Integer bod = idBodega != null ? idBodega : 0;
        LocalDateTime d = desde != null ? desde : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime h = hasta != null ? hasta : LocalDateTime.of(2999, 12, 31, 23, 59);

        Pageable pageable = PageRequest.of(page, size);
        Page<HistorialInventario> result = historialRepository.buscarAuditoria(prod, bod, d, h, pageable);

        List<AuditoriaHistorialDTO> content = result.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(content, result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public ResumenHistorialDTO resumenProducto(Integer idProducto) {
        Long total = historialRepository.contarMovimientosPorProducto(idProducto);
        Integer min = historialRepository.stockMinimoRegistrado(idProducto);
        Integer max = historialRepository.stockMaximoRegistrado(idProducto);
        LocalDateTime ultima = historialRepository.ultimaActualizacion(idProducto);
        return new ResumenHistorialDTO(total != null ? total : 0L, min, max, ultima);
    }

    private AuditoriaHistorialDTO toDTO(HistorialInventario h) {
        AuditoriaHistorialDTO dto = new AuditoriaHistorialDTO();
        dto.setIdHistorial(h.getIdHistorial());
        dto.setFecha(h.getFecha());
        dto.setStockAnterior(h.getStockAnterior());
        dto.setStockNuevo(h.getStockNuevo());
        Integer ant = h.getStockAnterior() != null ? h.getStockAnterior() : 0;
        Integer nue = h.getStockNuevo() != null ? h.getStockNuevo() : 0;
        dto.setDiferencia(nue - ant);
        dto.setMotivo(h.getMotivo());

        Inventario inv = h.getInventario();
        if (inv != null) {
            if (inv.getProducto() != null) {
                dto.setProducto(inv.getProducto().getNombre());
            }
            if (inv.getBodega() != null) {
                dto.setBodega(inv.getBodega().getNombre());
            }
        }

        if (h.getIdUsuario() != null) {
            Usuario u = usuarioRepository.findById(h.getIdUsuario()).orElse(null);
            if (u != null) {
                dto.setUsuario(u.getNombre() + " " + u.getApellido());
            }
        }

        return dto;
    }
}
