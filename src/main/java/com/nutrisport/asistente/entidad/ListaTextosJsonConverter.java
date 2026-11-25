package com.nutrisport.asistente.entidad;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * Lista de textos ↔ array JSON ({@code ["resumenDiario", "comidas"]}) para una columna jsonb.
 * <p>
 * Existe para no mapear la columna como {@code List<String>} con {@code @JdbcTypeCode(SqlTypes.JSON)} (ni siquiera
 * junto a este conversor): Hibernate registra entonces el tipo JSON para {@code List<String>} y las columnas
 * {@code List<String>} que son arrays de PostgreSQL (text[], en Alimento y Receta) dejan de validar. La entidad
 * declara la columna como jsonb y la convierte al escribir ({@code ?::jsonb}).
 */
@Converter
public class ListaTextosJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> LISTA = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> textos) {
        try {
            return JSON.writeValueAsString(textos == null ? List.of() : textos);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se ha podido convertir la lista a JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(JSON.readValue(json, LISTA));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("La columna no contiene un array JSON de textos: " + json, e);
        }
    }
}
