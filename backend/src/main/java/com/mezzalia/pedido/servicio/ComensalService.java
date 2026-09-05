package com.mezzalia.pedido.servicio;

import com.mezzalia.pedido.Comensal;
import com.mezzalia.pedido.ComensalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * NOTA PARA QUIEN INTEGRE ESTA RAMA: al momento de escribir esto, la rama
 * `feature/comensal-borrado-habeas-data` (RF-13, derecho de supresion del
 * comensal, ya pusheada, todavia sin mergear) TAMBIEN crea este mismo
 * archivo nuevo (con un metodo distinto, eliminarDatosComensal). Como
 * ambas ramas parten de `main` de forma independiente (mismo criterio que
 * el resto de este proyecto: "un PR = una cosa", ver CONTRIBUTING.md) y
 * las dos AGREGAN el mismo archivo nuevo, git va a marcar esto como un
 * conflicto add/add real al mergear la segunda de las dos -- no es un bug
 * de ninguna de las dos ramas, se resuelve combinando ambos metodos
 * (eliminarDatosComensal + reencriptarTodosLosComensales) en una sola
 * clase, con sus respectivos constructores/dependencias unidos.
 *
 * Rotacion de COMENSAL_ENCRYPTION_KEY (documentacion-tecnica.md §10) -- ver
 * el javadoc completo del procedimiento en
 * pedido.seguridad.AesGcmComensalCipher.
 */
@Service
@RequiredArgsConstructor
public class ComensalService {

    private final ComensalRepository comensalRepository;

    /**
     * Operacion de mantenimiento para SUPER_ADMIN, NO scoped a un tenant --
     * ver el javadoc de rotacion en AesGcmComensalCipher para el
     * procedimiento operativo completo. Lee cada Comensal (el descifrado de
     * Comensal.getTelefono() intenta primero COMENSAL_ENCRYPTION_KEY y, si
     * falla, COMENSAL_ENCRYPTION_KEY_PREVIOUS -- ver
     * TelefonoEncriptadoConverter/AesGcmComensalCipher.descifrar) y lo
     * vuelve a escribir con ComensalRepository.actualizarTelefono, que
     * re-cifra SIEMPRE con la clave vigente -- ver el javadoc de ese metodo
     * sobre por que es un UPDATE JPQL explicito y no
     * "cargar+setTelefono+save" (el dirty-checking de Hibernate no
     * detectaria ningun cambio: el valor Java -- el telefono ya descifrado
     * -- es identico antes y despues, aunque la representacion cifrada en
     * la columna deba cambiar de clave).
     *
     * findAll() sin tenant a proposito, mismo patron que
     * TenantService.listarTenants(): SUPER_ADMIN no tiene tenant_id propio,
     * y esto es intencionalmente una operacion cross-tenant -- la clave de
     * cifrado es una sola para todo el sistema, rotarla aplica a todos los
     * restaurantes a la vez.
     *
     * LIMITE CONOCIDO: procesa todos los comensales en una sola transaccion
     * -- aceptable para el volumen esperado de un puñado de tenants
     * Starter/Pro (documentacion-tecnica.md §1.2). Si el numero de
     * comensales crece mucho, esto necesitaria paginacion/lotes, mismo tipo
     * de limite de escala ya aceptado en otras partes de este proyecto (ver
     * LoginAttemptService, AlmacenamientoImagenService). Si CUALQUIER fila
     * falla al descifrar (ninguna de las dos claves configuradas le
     * corresponde), la transaccion entera hace rollback -- es preferible un
     * error ruidoso e inmediato a una reencriptacion parcial silenciosa.
     *
     * @return cuantos comensales con telefono no nulo fueron reencriptados.
     */
    @Transactional
    public int reencriptarTodosLosComensales() {
        List<Comensal> todos = comensalRepository.findAll();
        int reencriptados = 0;
        for (Comensal comensal : todos) {
            String telefono = comensal.getTelefono();
            if (telefono != null) {
                comensalRepository.actualizarTelefono(comensal.getId(), telefono);
                reencriptados++;
            }
        }
        return reencriptados;
    }
}
