package com.nutrisport.ia.repositorio;

import com.nutrisport.ia.entidad.FuncionIa;
import com.nutrisport.ia.entidad.UsoIa;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a la tabla {@code uso_ia}. */
public interface UsoIaRepositorio extends JpaRepository<UsoIa, UUID> {

    /**
     * Llamadas con éxito del usuario a esas funciones desde un instante (incluido). Las fallidas no
     * consumen el límite diario: el usuario no tiene la culpa de que el proveedor falle.
     */
    @Query("""
            select count(u) from UsoIa u
             where u.usuario.id = :usuarioId and u.funcion in :funciones and u.creadoEn >= :desde
               and u.exito = true
            """)
    long contarExitosas(@Param("usuarioId") UUID usuarioId, @Param("funciones") Collection<FuncionIa> funciones,
            @Param("desde") OffsetDateTime desde);

    /** Coste estimado total (USD) de todas las llamadas desde un instante (incluido); 0 si no hay ninguna. */
    @Query("select coalesce(sum(u.costeEstimado), 0) from UsoIa u where u.creadoEn >= :desde")
    BigDecimal sumarCosteDesde(@Param("desde") OffsetDateTime desde);
}
