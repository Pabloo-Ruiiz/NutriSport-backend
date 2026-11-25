package com.nutrisport.compra.repositorio;

import com.nutrisport.compra.entidad.ListaCompra;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Listas de la compra de cada usuario (§6.1). Todas las consultas filtran por el usuario: las de otro no existen. */
public interface ListaCompraRepositorio extends JpaRepository<ListaCompra, UUID> {

    /** Una lista propia con sus productos y sus alimentos (para el detalle). */
    @EntityGraph(attributePaths = {"productos", "productos.alimento"})
    Optional<ListaCompra> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /** Listas del usuario, la más reciente primero. */
    @Query(value = """
            select l from ListaCompra l
             where l.usuario.id = :usuarioId
             order by l.creadoEn desc, l.id
            """,
            countQuery = "select count(l) from ListaCompra l where l.usuario.id = :usuarioId")
    Page<ListaCompra> buscar(@Param("usuarioId") UUID usuarioId, Pageable pageable);
}
