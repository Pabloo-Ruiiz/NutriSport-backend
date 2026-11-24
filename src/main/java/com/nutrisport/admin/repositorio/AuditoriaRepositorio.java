package com.nutrisport.admin.repositorio;

import com.nutrisport.admin.entidad.Auditoria;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los registros de auditoría. */
public interface AuditoriaRepositorio extends JpaRepository<Auditoria, UUID> {
}
