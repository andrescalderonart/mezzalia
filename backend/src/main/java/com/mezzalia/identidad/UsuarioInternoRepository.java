package com.mezzalia.identidad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioInternoRepository extends JpaRepository<UsuarioInterno, Long> {

    /**
     * Búsqueda GLOBAL (sin filtro de tenant) a propósito: en el momento del
     * login todavía no sabemos el tenant del usuario — es justamente lo que
     * este método resuelve, junto con el rol, antes de emitir el JWT. Por
     * eso el username es único a nivel de toda la plataforma (decisión de
     * producto: "usuario y contraseña", sin selector de restaurante previo).
     */
    Optional<UsuarioInterno> findByUsername(String username);

    boolean existsByUsername(String username);
}
