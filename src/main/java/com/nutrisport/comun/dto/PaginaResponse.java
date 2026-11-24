package com.nutrisport.comun.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Respuesta paginada de la API (§5.4): {@code {content, page, size, totalElements, totalPages}}.
 * Evita serializar directamente {@link Page}, cuyo formato JSON no es estable.
 *
 * @param content       elementos de la página
 * @param page          número de página, empezando en 0
 * @param size          tamaño de página solicitado
 * @param totalElements número total de elementos
 * @param totalPages    número total de páginas
 */
public record PaginaResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PaginaResponse {
        content = List.copyOf(content);
    }

    /** Crea la respuesta a partir de una página cuyos elementos ya son DTO. */
    public static <T> PaginaResponse<T> de(Page<T> pagina) {
        return new PaginaResponse<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }

    /** Crea la respuesta convirtiendo cada elemento (normalmente una entidad) con el conversor dado. */
    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<? super E, ? extends T> conversor) {
        List<T> contenido = pagina.getContent().stream().<T>map(conversor).toList();
        return new PaginaResponse<>(contenido, pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}
