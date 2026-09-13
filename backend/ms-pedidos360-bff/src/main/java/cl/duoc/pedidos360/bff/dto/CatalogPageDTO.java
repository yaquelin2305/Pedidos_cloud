package cl.duoc.pedidos360.bff.dto;

import java.util.List;

/**
 * catalog expone /api/catalog/products paginado (Page<ProductResponseDTO>). El BFF solo
 * necesita el contenido para devolverselo al frontend como lista plana, asi que el resto de
 * los campos de la pagina (totalElements, number, etc.) ni se declaran aqui.
 */
public record CatalogPageDTO(List<ProductResponseDTO> content) {
}
