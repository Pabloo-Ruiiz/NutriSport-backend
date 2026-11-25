package com.nutrisport.compra.repositorio;

import com.nutrisport.compra.entidad.ProductoCompra;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Productos de las listas de la compra (§6.1). */
public interface ProductoCompraRepositorio extends JpaRepository<ProductoCompra, UUID> {

    /** Un producto de una lista del usuario, con su lista y su alimento; vacío si la lista es de otro. */
    @EntityGraph(attributePaths = {"lista", "alimento"})
    @Query("""
            select p from ProductoCompra p
             where p.id = :id and p.lista.id = :listaId and p.lista.usuario.id = :usuarioId
            """)
    Optional<ProductoCompra> buscarDelUsuario(@Param("id") UUID id, @Param("listaId") UUID listaId,
            @Param("usuarioId") UUID usuarioId);
}
