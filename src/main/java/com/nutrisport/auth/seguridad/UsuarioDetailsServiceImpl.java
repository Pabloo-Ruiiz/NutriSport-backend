package com.nutrisport.auth.seguridad;

import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga los usuarios desde la tabla {@code usuario}: por email en el login y por id en cada
 * petición con JWT. Los mensajes no incluyen el email para no filtrarlo en los logs.
 */
@Service
@Transactional(readOnly = true)
public class UsuarioDetailsServiceImpl implements UserDetailsService {

    private static final String NO_ENCONTRADO = "Usuario no encontrado";

    private final UsuarioRepositorio usuarioRepositorio;

    public UsuarioDetailsServiceImpl(UsuarioRepositorio usuarioRepositorio) {
        this.usuarioRepositorio = usuarioRepositorio;
    }

    /** Busca por email normalizado (sin espacios y en minúsculas). */
    @Override
    public UsuarioAutenticado loadUserByUsername(String email) {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException(NO_ENCONTRADO);
        }
        String normalizado = email.trim().toLowerCase(Locale.ROOT);
        return usuarioRepositorio.findByEmail(normalizado)
                .map(UsuarioAutenticado::de)
                .orElseThrow(() -> new UsernameNotFoundException(NO_ENCONTRADO));
    }

    /** Carga el usuario del {@code sub} del JWT; así un bloqueo surte efecto en la siguiente petición. */
    public UsuarioAutenticado cargarPorId(UUID id) {
        if (id == null) {
            throw new UsernameNotFoundException(NO_ENCONTRADO);
        }
        return usuarioRepositorio.findById(id)
                .map(UsuarioAutenticado::de)
                .orElseThrow(() -> new UsernameNotFoundException(NO_ENCONTRADO));
    }
}
