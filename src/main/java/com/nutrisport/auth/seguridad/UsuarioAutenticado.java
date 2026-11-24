package com.nutrisport.auth.seguridad;

import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Usuario;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuario autenticado en el contexto de seguridad. Es una copia inmutable de los datos de
 * {@link Usuario} que necesita Spring Security; nunca se devuelve en la API.
 * <p>
 * Implementa {@link CredentialsContainer}: tras un login correcto Spring Security llama a
 * {@link #eraseCredentials()} y el hash de la contraseña deja de estar en memoria.
 */
public class UsuarioAutenticado implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private String passwordHash;
    private final Set<Rol> roles;
    private final EstadoUsuario estado;
    /** Consentimiento de datos de salud vigente (§8.3); lo comprueba el InterceptorConsentimiento. */
    private final boolean consentimientoVigente;

    /** Usuario con el consentimiento de salud vigente. */
    public UsuarioAutenticado(UUID id, String email, String passwordHash, Set<Rol> roles, EstadoUsuario estado) {
        this(id, email, passwordHash, roles, estado, true);
    }

    public UsuarioAutenticado(UUID id, String email, String passwordHash, Set<Rol> roles, EstadoUsuario estado,
            boolean consentimientoVigente) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
        this.estado = estado;
        this.consentimientoVigente = consentimientoVigente;
    }

    public static UsuarioAutenticado de(Usuario usuario) {
        return new UsuarioAutenticado(usuario.getId(), usuario.getEmail(), usuario.getPasswordHash(),
                usuario.getRoles(), usuario.getEstado(), usuario.consentimientoVigente());
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Set<Rol> getRoles() {
        return roles;
    }

    public EstadoUsuario getEstado() {
        return estado;
    }

    public boolean isConsentimientoVigente() {
        return consentimientoVigente;
    }

    /** Autoridades {@code ROLE_DEPORTISTA} y {@code ROLE_ADMIN} (§8.2). */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> autoridades = roles.stream()
                .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol.name()))
                .toList();
        return autoridades;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** El nombre de usuario es el email. */
    @Override
    public String getUsername() {
        return email;
    }

    /** Una cuenta BLOQUEADA o ELIMINADA no puede acceder. */
    @Override
    public boolean isEnabled() {
        return estado != EstadoUsuario.BLOQUEADO && estado != EstadoUsuario.ELIMINADO;
    }

    @Override
    public boolean isAccountNonLocked() {
        return estado != EstadoUsuario.BLOQUEADO;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof UsuarioAutenticado usuario && id != null && id.equals(usuario.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
