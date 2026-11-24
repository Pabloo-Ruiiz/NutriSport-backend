package com.nutrisport.catalogo.servicio;

import com.nutrisport.catalogo.dto.CategoriaResponse;
import com.nutrisport.catalogo.entidad.TipoCategoria;
import com.nutrisport.catalogo.mapper.CategoriaMapper;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de las categorías del catálogo (§7.3). */
@Service
@Transactional(readOnly = true)
public class CategoriaServicio {

    private final CategoriaRepositorio categoriaRepositorio;
    private final CategoriaMapper categoriaMapper;

    public CategoriaServicio(CategoriaRepositorio categoriaRepositorio, CategoriaMapper categoriaMapper) {
        this.categoriaRepositorio = categoriaRepositorio;
        this.categoriaMapper = categoriaMapper;
    }

    /** Categorías del tipo indicado, en su orden de presentación. */
    public List<CategoriaResponse> listar(TipoCategoria tipo) {
        return categoriaMapper.aRespuestas(categoriaRepositorio.findByTipoOrderByOrden(tipo));
    }
}
