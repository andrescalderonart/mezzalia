package com.mezzalia.pedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComensalRepository extends JpaRepository<Comensal, Long> {
}
