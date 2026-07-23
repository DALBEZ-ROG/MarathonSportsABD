package com.marathon.dto.bom;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Reemplazo completo del BOM de un producto fabricado.
 * Debe traer al menos 1 linea.
 */
public class ListaMaterialesItemDTO {

    @NotEmpty(message = "Debe definir al menos 1 material en la lista de materiales")
    @Valid
    private List<ListaMaterialesRequestDTO> items;

    public ListaMaterialesItemDTO() {}

    public List<ListaMaterialesRequestDTO> getItems() { return items; }
    public void setItems(List<ListaMaterialesRequestDTO> items) { this.items = items; }
}
