package com.mezzalia.menu.controlador;

import com.mezzalia.mesa.SesionMesa;
import com.mezzalia.mesa.excepcion.MesaExceptions;
import com.mezzalia.mesa.servicio.MesaService;
import com.mezzalia.menu.dto.CartaPublicaDto;
import com.mezzalia.pedido.Pedido;
import com.mezzalia.pedido.dto.CrearPedidoRequest;
import com.mezzalia.pedido.dto.PedidoDto;
import com.mezzalia.pedido.dto.PedidoItemDto;
import com.mezzalia.pedido.servicio.PedidoService;

import com.mezzalia.menu.dto.CategoriaPublicaDto;
import com.mezzalia.menu.dto.ItemPublicoDto;
import com.mezzalia.menu.servicio.MenuService;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantFilterActivator;
import com.mezzalia.tenant.dto.BrandingDto;
import com.mezzalia.tenant.servicio.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/publico/mesas")
@RequiredArgsConstructor
public class MenuPublicoController {

    private final MesaService mesaService;
    private final MenuService menuService;
    private final TenantService tenantService;
    private final TenantFilterActivator tenantFilterActivator;
    private final PedidoService pedidoService;

    /**
     * RF-01/RF-02: el comensal ve la carta vigente -- incluidos los ítems
     * agotados, que se muestran distintos pero NO desaparecen -- sin login,
     * resolviendo tenant + mesa a partir del token del QR (RF-08).
     *
     * Esto NO es solo "llamar a MenuService directo": ver
     * TenantFilterActivator para por qué, sin activar el filtro de Hibernate
     * a mano acá, esta llamada devolvería categorías/ítems de TODOS los
     * tenants, no solo el de esta mesa -- la fuga de datos entre
     * restaurantes que documentacion-tecnica.md §6 marca como el riesgo #1
     * de este tipo de sistema.
     */
    @GetMapping("/{token}/carta")
    public ResponseEntity<CartaPublicaDto> obtenerCarta(@PathVariable String token) {
        SesionMesa sesion = mesaService.resolverSesionValida(token);
        Long tenantId = sesion.getTenantId();

        Tenant tenant = tenantService.obtenerPorId(tenantId);
        if (tenant.estaSuspendido()) {
            // Mismo criterio que MesaExceptions en general: 404 generico,
            // nunca un estado que le confirme a un desconocido que el
            // restaurante existe pero esta suspendido.
            throw new MesaExceptions.RestauranteNoDisponibleException();
        }

        CartaPublicaDto carta = tenantFilterActivator.ejecutarConTenant(tenantId, () -> {
            var categorias = menuService.listarCategorias().stream()
                    .map(categoria -> {
                        var items = menuService.listarItemsDeCategoria(categoria.getId()).stream()
                                .map(ItemPublicoDto::desde)
                                .toList();
                        return CategoriaPublicaDto.desde(categoria, items);
                    })
                    .toList();

            BrandingDto branding = new BrandingDto(
                    tenant.getLogoUrl(), tenant.getColorPrimario(), tenant.getColorSecundario());
            return new CartaPublicaDto(sesion.getMesaId(), tenant.getNombre(), branding, categorias);
        });

        return ResponseEntity.ok(carta);
    }

    /**
     * RF-03..RF-08: implementacion real -- reemplaza el 501 explicito que
     * habia antes. Mismo patron de resolucion publica que obtenerCarta: el
     * token de la URL es la unica credencial, se resuelve tenant+mesa a
     * partir de el, y se activa el filtro de tenant a mano
     * (TenantFilterActivator) para poder reusar PedidoService/MenuService
     * sin fuga entre tenants -- ver el comentario de obtenerCarta arriba
     * para el detalle de por que esto no es opcional.
     */
    @PostMapping("/{token}/pedidos")
    public ResponseEntity<PedidoDto> crearPedido(@PathVariable String token, @Valid @RequestBody CrearPedidoRequest request) {
        SesionMesa sesion = mesaService.resolverSesionValida(token);
        Long tenantId = sesion.getTenantId();

        Tenant tenant = tenantService.obtenerPorId(tenantId);
        if (tenant.estaSuspendido()) {
            // Mismo criterio que en obtenerCarta: 404 generico, nunca un
            // estado que le confirme a un desconocido que el restaurante
            // existe pero esta suspendido.
            throw new MesaExceptions.RestauranteNoDisponibleException();
        }

        PedidoDto dto = tenantFilterActivator.ejecutarConTenant(tenantId, () -> {
            Pedido pedido = pedidoService.crearPedido(
                    sesion.getMesaId(), request.claveIdempotencia(), request.items(),
                    request.notas(), request.comensal());
            List<PedidoItemDto> items = pedidoService.listarItems(pedido.getId()).stream()
                    .map(PedidoItemDto::desde)
                    .toList();
            return PedidoDto.desde(pedido, items);
        });

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
