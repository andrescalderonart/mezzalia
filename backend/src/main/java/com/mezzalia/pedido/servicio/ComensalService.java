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
 * Derecho de supresion del comensal (RF-13, documentacion-tecnica.md §10):
 * "un endpoint de borrado a solicitud del titular que efectivamente elimine
 * el dato, no solo lo oculte en la UI". Servicio separado de PedidoService
 * a proposito -- esto es una operacion sobre datos personales (Habeas Data),
 * una responsabilidad distinta de "administrar el ciclo de vida de un
 * pedido", aunque vivan en el mismo modulo por ahora (Comensal no justifica
 * un modulo propio todavia).
 *
 * IMPORTANTE: esto es solo el mecanismo tecnico de borrado. El texto del
 * aviso de privacidad, el flujo de consentimiento y cuanto tiempo se
 * retienen los datos si el comensal nunca vuelve siguen sin resolver
 * (documentacion-tecnica.md §15.2) -- explicitamente trabajo legal, no de
 * arquitectura, fuera de alcance de este cambio.
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
     */
    private Comensal obtenerComensalDelTenant(Long id) {
        Comensal comensal = comensalRepository.findById(id)
                .orElseThrow(() -> new PedidoExceptions.ComensalNoEncontradoException(id));
        Long tenantActual = tenantActual();
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
}
