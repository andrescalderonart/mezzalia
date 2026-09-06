package com.mezzalia.pedido.servicio;

import com.mezzalia.pedido.Comensal;
import com.mezzalia.pedido.ComensalRepository;
import com.mezzalia.pedido.Pedido;
import com.mezzalia.pedido.PedidoRepository;
import com.mezzalia.pedido.excepcion.PedidoExceptions;
import com.mezzalia.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dos responsabilidades sobre datos personales del comensal que terminaron
 * en el mismo archivo por una colision de ramas real, no por diseño: esta
 * clase la crearon en paralelo `feature/comensal-borrado-habeas-data`
 * (RF-13, derecho de supresion -- ya mergeada a main junto con su propio
 * fix de orden tenant/findById) y `feature/rotacion-clave-cifrado-comensal`
 * (rotacion de COMENSAL_ENCRYPTION_KEY, documentacion-tecnica.md §10),
 * cada una agregando el mismo archivo nuevo de forma independiente. Git lo
 * marco como conflicto add/add al mergear `main` en esta segunda rama; se
 * resuelve combinando ambos metodos aca, sin tocar la logica de ninguno de
 * los dos (cada uno ya viene con su propia cobertura de tests).
 *
 * Derecho de supresion (RF-13, §10): "un endpoint de borrado a solicitud
 * del titular que efectivamente elimine el dato, no solo lo oculte en la
 * UI". IMPORTANTE: esto es solo el mecanismo tecnico de borrado -- el
 * texto del aviso de privacidad, el flujo de consentimiento y cuanto
 * tiempo se retienen los datos si el comensal nunca vuelve siguen sin
 * resolver (§15.2), explicitamente trabajo legal, no de arquitectura.
 *
 * Rotacion de COMENSAL_ENCRYPTION_KEY (§10) -- ver el javadoc completo del
 * procedimiento en pedido.seguridad.AesGcmComensalCipher.
 */
@Service
@RequiredArgsConstructor
public class ComensalService {

    private final ComensalRepository comensalRepository;
    private final PedidoRepository pedidoRepository;

    /**
     * Borra de verdad la fila de `comensales` -- no un soft-delete, no un
     * "ocultar en la UI" (la instruccion explicita de §10 es "elimine el
     * dato"). `comensal_id` en `pedidos` es NULLABLE a proposito (ver
     * Pedido.java) precisamente para que un pedido pueda sobrevivir sin sus
     * datos personales asociados: cada pedido que referencia a este
     * comensal se desvincula primero (comensal_id -> null) en la misma
     * transaccion, porque la FK fk_pedidos_comensal (V11__pedidos.sql) no
     * tiene ON DELETE SET NULL -- un DELETE directo sobre `comensales`
     * fallaria por la restriccion de integridad referencial si se saltara
     * este paso. El pedido en si (monto, items, mesa, fecha) SI se
     * conserva: es informacion operativa/de facturacion del restaurante,
     * no un dato personal del comensal.
     */
    @Transactional
    public void eliminarDatosComensal(Long comensalId) {
        Comensal comensal = obtenerComensalDelTenant(comensalId);

        List<Pedido> pedidosVinculados = pedidoRepository.findByComensalId(comensalId);
        for (Pedido pedido : pedidosVinculados) {
            pedido.setComensalId(null);
        }
        pedidoRepository.saveAll(pedidosVinculados);

        comensalRepository.delete(comensal);
    }

    /**
     * Defensa EXPLICITA igual que obtenerPedido/obtenerMesa/obtenerItem en
     * el resto del sistema -- ver el comentario largo en MenuService sobre
     * por que una carga por id (findById) no se confia solo al filtro de
     * Hibernate. El id de comensal llega directo del cliente (path
     * variable del DELETE), asi que es exactamente el caso que ese patron
     * cubre: nunca "no autorizado", siempre "no encontrado", para no
     * confirmarle a un tenant que el id existe en otro tenant.
     *
     * tenantActual() se llama ANTES del findById, no despues -- mismo
     * orden que MesaService.listarMesas()/crearMesa(). Si se llama despues,
     * un SUPER_ADMIN (sin tenant_id, ver JwtAuthenticationFilter) pidiendo
     * un id que no existe recibe ComensalNoEncontradoException en vez de
     * SinTenantEnContextoException, porque el orElseThrow del findById
     * gana la carrera antes de que se note que ni siquiera hay tenant en
     * contexto -- exactamente el bug que este orden evita (encontrado por
     * ComensalServiceTest.unSuperAdminSinTenantEnContextoNoPuedeEliminarDatosDeComensal
     * fallando en CI contra un id inexistente).
     */
    private Comensal obtenerComensalDelTenant(Long id) {
        Long tenantActual = tenantActual();
        Comensal comensal = comensalRepository.findById(id)
                .orElseThrow(() -> new PedidoExceptions.ComensalNoEncontradoException(id));
        if (!tenantActual.equals(comensal.getTenantId())) {
            throw new PedidoExceptions.ComensalNoEncontradoException(id);
        }
        return comensal;
    }

    private Long tenantActual() {
        Long tenantId = TenantContext.obtenerTenantId();
        if (tenantId == null) {
            throw new PedidoExceptions.SinTenantEnContextoException();
        }
        return tenantId;
    }

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
