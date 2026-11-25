package com.nutrisport.admin.dto;

import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.recetas.entidad.Dificultad;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Receta propia pública creada o editada por un administrador (§7.10). Se crea sin publicar. */
public record RecetaAdminRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String nombre,

        @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres")
        String descripcion,

        @NotNull(message = "La categoría es obligatoria")
        UUID categoriaId,

        @NotNull(message = "Las raciones son obligatorias")
        @Min(value = 1, message = "Las raciones deben estar entre 1 y 20")
        @Max(value = 20, message = "Las raciones deben estar entre 1 y 20")
        Integer raciones,

        @Min(value = 1, message = "El tiempo debe estar entre 1 y 600 minutos")
        @Max(value = 600, message = "El tiempo debe estar entre 1 y 600 minutos")
        Integer tiempoPreparacionMin,

        Dificultad dificultad,

        @Size(max = 7, message = "Como mucho 7 franjas")
        Set<TipoComida> franjasAptas,

        @Size(max = 10, message = "Como mucho 10 etiquetas")
        List<@NotBlank @Size(max = 40, message = "Una etiqueta no puede superar los 40 caracteres") String> etiquetas,

        @Size(max = 30, message = "Como mucho 30 pasos")
        List<@NotBlank @Size(max = 1000, message = "Un paso no puede superar los 1000 caracteres") String> pasos,

        @NotNull(message = "Los ingredientes son obligatorios")
        @Size(min = 1, max = 40, message = "La receta debe tener entre 1 y 40 ingredientes")
        List<@Valid @NotNull IngredienteRecetaAdminRequest> ingredientes) {
}
