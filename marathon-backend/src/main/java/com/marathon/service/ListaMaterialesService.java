package com.marathon.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marathon.dto.bom.CostoProduccionEstimadoDTO;
import com.marathon.dto.bom.ListaMaterialesItemDTO;
import com.marathon.dto.bom.ListaMaterialesRequestDTO;
import com.marathon.dto.bom.ListaMaterialesResponseDTO;
import com.marathon.dto.bom.ListaMaterialesResponseDTO.MateriaPrimaSimpleDTO;
import com.marathon.dto.producto.ProductoResponseDTO;
import com.marathon.exception.ResourceNotFoundException;
import com.marathon.exception.ValidationException;
import com.marathon.model.ListaMateriales;
import com.marathon.model.MateriaPrima;
import com.marathon.model.Producto;
import com.marathon.repository.ListaMaterialesRepository;
import com.marathon.repository.MateriaPrimaRepository;
import com.marathon.repository.ProductoRepository;

@Service
public class ListaMaterialesService {

    private final ListaMaterialesRepository listaMaterialesRepository;
    private final ProductoRepository productoRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final ProductoService productoService;

    public ListaMaterialesService(ListaMaterialesRepository listaMaterialesRepository,
                                  ProductoRepository productoRepository,
                                  MateriaPrimaRepository materiaPrimaRepository,
                                  ProductoService productoService) {
        this.listaMaterialesRepository = listaMaterialesRepository;
        this.productoRepository = productoRepository;
        this.materiaPrimaRepository = materiaPrimaRepository;
        this.productoService = productoService;
    }

    /**
     * Retorna las lineas de BOM activas de un producto.
     */
    public List<ListaMaterialesResponseDTO> obtenerBomDeProducto(Integer idProducto) {
        productoRepository.findById(idProducto)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", idProducto));
        return listaMaterialesRepository
                .findByProductoIdProductoAndEstado(idProducto, "activo")
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Reemplaza por completo el BOM de un producto fabricado.
     * <p>
     * El CHECK UNIQUE (id_producto, id_materia_prima) impide conservar una
     * linea vieja inactiva y a la vez insertar una nueva activa para la misma
     * materia prima. Por eso se hace un "upsert": se desactivan todas las
     * lineas actuales, luego las materias primas que reaparecen en el nuevo
     * BOM se REACTIVAN en el mismo registro (nuevo valor de cantidad) y las
     * que ya no aparecen quedan inactivas (historial preservado).
     */
    @Transactional
    public List<ListaMaterialesResponseDTO> definirBom(Integer idProducto,
                                                       ListaMaterialesItemDTO dto,
                                                       Integer idUsuarioActual) {
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", idProducto));

        if (!"fabricado".equals(producto.getOrigen())) {
            throw new ValidationException(
                "El producto debe tener origen=fabricado para definir lista de materiales. "
                + "Cambie el origen del producto primero.");
        }

        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new ValidationException(
                "Debe definir al menos 1 material en la lista de materiales");
        }

        // Todas las lineas existentes (cualquier estado) indexadas por materia prima.
        List<ListaMateriales> existentes = listaMaterialesRepository
                .findByProductoIdProductoAndEstado(idProducto, "activo");
        // Recuperamos tambien las inactivas para poder reactivar sin violar UNIQUE.
        List<ListaMateriales> todas = new ArrayList<>(existentes);
        todas.addAll(listaMaterialesRepository
                .findByProductoIdProductoAndEstado(idProducto, "inactivo"));

        Map<Integer, ListaMateriales> porMateria = new HashMap<>();
        for (ListaMateriales lm : todas) {
            porMateria.put(lm.getMateriaPrima().getIdMateriaPrima(), lm);
        }

        // Desactivar todas las lineas actuales.
        for (ListaMateriales lm : existentes) {
            lm.setEstado("inactivo");
        }

        // Aplicar las nuevas lineas (upsert), evitando duplicar materia prima.
        List<ListaMateriales> aGuardar = new ArrayList<>(existentes);
        java.util.Set<Integer> vistas = new java.util.HashSet<>();
        for (ListaMaterialesRequestDTO item : dto.getItems()) {
            Integer idMp = item.getIdMateriaPrima();
            if (!vistas.add(idMp)) {
                throw new ValidationException(
                    "La materia prima id " + idMp + " esta repetida en la lista de materiales");
            }
            MateriaPrima mp = materiaPrimaRepository.findById(idMp)
                    .orElseThrow(() -> new ResourceNotFoundException("Materia prima", idMp));

            ListaMateriales linea = porMateria.get(idMp);
            if (linea == null) {
                linea = new ListaMateriales();
                linea.setProducto(producto);
                linea.setMateriaPrima(mp);
            }
            linea.setCantidadNecesaria(item.getCantidadNecesaria());
            linea.setEstado("activo");
            aGuardar.add(linea);
        }

        listaMaterialesRepository.saveAll(aGuardar);

        return obtenerBomDeProducto(idProducto);
    }

    /**
     * Cambia el origen del producto delegando en ProductoService, que atrapa
     * el error del trigger de BD y lo traduce a un mensaje amigable.
     */
    @Transactional
    public ProductoResponseDTO cambiarOrigenProducto(Integer idProducto,
                                                     String nuevoOrigen,
                                                     Integer idUsuarioActual) {
        return productoService.cambiarOrigen(idProducto, nuevoOrigen);
    }

    /**
     * F29 — Costo estimado de producir 1 unidad: suma de cantidad_necesaria ×
     * costo_unitario_promedio de cada materia prima del BOM activo. Incluye
     * precio de venta y margen bruto (sin mano de obra ni indirectos, que son
     * específicos de cada orden).
     */
    public CostoProduccionEstimadoDTO calcularCostoEstimado(Integer idProducto) {
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", idProducto));

        List<ListaMateriales> bom = listaMaterialesRepository
                .findByProductoIdProductoAndEstado(idProducto, "activo");
        if (bom.isEmpty()) {
            throw new ValidationException(
                "El producto no tiene lista de materiales definida. Configure el BOM antes de estimar costos.");
        }

        List<CostoProduccionEstimadoDTO.CostoMaterialEstimadoDTO> materiales = new ArrayList<>();
        BigDecimal costoUnitarioTotal = BigDecimal.ZERO;
        boolean faltaCosto = false;

        for (ListaMateriales linea : bom) {
            MateriaPrima mp = linea.getMateriaPrima();
            BigDecimal costoUnit = mp.getCostoUnitarioPromedio() != null
                    ? mp.getCostoUnitarioPromedio() : BigDecimal.ZERO;
            BigDecimal costoLinea = linea.getCantidadNecesaria().multiply(costoUnit)
                    .setScale(4, java.math.RoundingMode.HALF_UP);
            if (costoUnit.compareTo(BigDecimal.ZERO) == 0) faltaCosto = true;
            costoUnitarioTotal = costoUnitarioTotal.add(costoLinea);
            materiales.add(new CostoProduccionEstimadoDTO.CostoMaterialEstimadoDTO(
                    mp.getIdMateriaPrima(), mp.getNombre(), linea.getCantidadNecesaria(),
                    costoUnit, costoLinea));
        }
        costoUnitarioTotal = costoUnitarioTotal.setScale(4, java.math.RoundingMode.HALF_UP);

        CostoProduccionEstimadoDTO dto = new CostoProduccionEstimadoDTO();
        dto.setIdProducto(producto.getIdProducto());
        dto.setNombreProducto(producto.getNombre());
        dto.setMateriales(materiales);
        dto.setCostoMateriaPrimaUnitario(costoUnitarioTotal);

        BigDecimal precioVenta = producto.getPrecio() != null ? producto.getPrecio() : BigDecimal.ZERO;
        dto.setPrecioVenta(precioVenta);
        BigDecimal margen = precioVenta.subtract(costoUnitarioTotal).setScale(2, java.math.RoundingMode.HALF_UP);
        dto.setMargenBruto(margen);
        if (precioVenta.compareTo(BigDecimal.ZERO) > 0) {
            dto.setMargenPorcentaje(margen.multiply(BigDecimal.valueOf(100))
                    .divide(precioVenta, 2, java.math.RoundingMode.HALF_UP));
        } else {
            dto.setMargenPorcentaje(BigDecimal.ZERO);
        }

        String adv = "Estimado de materia prima únicamente; NO incluye mano de obra ni indirectos (son específicos de cada orden de producción).";
        if (faltaCosto) {
            adv += " Algunos materiales aún no tienen costo registrado (nunca se han recibido por compra).";
        }
        dto.setAdvertencia(adv);
        return dto;
    }

    private ListaMaterialesResponseDTO toDTO(ListaMateriales lm) {
        ListaMaterialesResponseDTO dto = new ListaMaterialesResponseDTO();
        dto.setIdBom(lm.getIdBom());
        dto.setCantidadNecesaria(lm.getCantidadNecesaria());
        dto.setEstado(lm.getEstado());
        dto.setCreatedAt(lm.getCreatedAt());

        MateriaPrima mp = lm.getMateriaPrima();
        String unidad = mp.getUnidadMedida() != null ? mp.getUnidadMedida().getNombre() : null;
        dto.setMateriaPrima(new MateriaPrimaSimpleDTO(
                mp.getIdMateriaPrima(), mp.getNombre(), unidad));
        return dto;
    }
}
