package com.mezzalia.pedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ComensalRepository extends JpaRepository<Comensal, Long> {

    /**
     * Uso exclusivo de ComensalService.reencriptarTodosLosComensales
     * (rotacion de COMENSAL_ENCRYPTION_KEY, ver AesGcmComensalCipher).
     *
     * UPDATE JPQL explicito en vez de "cargar la entidad, hacer
     * setTelefono(getTelefono()) y guardar" a proposito: Hibernate hace
     * dirty-checking comparando el VALOR JAVA del atributo (el telefono ya
     * DESCIFRADO) contra su snapshot cargado -- si el texto plano no
     * cambio, Hibernate no genera ningun UPDATE, aunque la intencion sea
     * cambiar la clave con la que queda cifrado en la columna. Un UPDATE
     * JPQL explicito por id fuerza el INSERT/UPDATE real sin depender de
     * ese dirty-checking, y Hibernate SI aplica el AttributeConverter del
     * atributo (TelefonoEncriptadoConverter) al valor del parametro de un
     * UPDATE JPQL sobre un atributo convertido -- por eso este metodo cifra
     * con la clave VIGENTE sin que el llamador tenga que invocar el
     * converter a mano.
     *
     * No es @Query(nativeQuery = true) (JPQL sobre la entidad, no SQL
     * crudo) -- no aplica la revision de "toda query nativa necesita
     * filtro de tenant explicito" (documentacion-tecnica.md §6) del mismo
     * modo, pero igual: por id (no un WHERE amplio) y de uso exclusivo de
     * una operacion SUPER_ADMIN intencionalmente cross-tenant (ver
     * ComensalService.reencriptarTodosLosComensales), no una fuga -- es
     * SUPER_ADMIN reencriptando la columna de TODOS los tenants a la vez,
     * que es exactamente lo que la operacion de rotacion de clave necesita.
     */
    @Modifying
    @Query("UPDATE Comensal c SET c.telefono = :telefono WHERE c.id = :id")
    int actualizarTelefono(@Param("id") Long id, @Param("telefono") String telefonoEnTextoPlano);
}
