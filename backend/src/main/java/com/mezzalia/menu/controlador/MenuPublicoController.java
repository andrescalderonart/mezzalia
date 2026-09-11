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
import com.mezzalia.sucursal.SucursalContext;
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
import java.util.function.Supplier;

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
     *
     * 2026-09-10: mismo argumento aplica ahora a sucursal, no solo a tenant
     * -- cada sucursal tiene su propia carta, y esta mesa pertenece a UNA
     * sucursal fija (nunca cambia). ejecutarConSucursal puebla
     * SucursalContext con esa sucursal, para que MenuService (que ahora
     * exige sucursal seleccionada siempre, ver su javadoc) devuelva la
     * carta correcta sin tener que cambiar la firma de sus métodos.
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

        Long sucursalId = mesaService.obtenerSucursalIdDeMesa(sesion.getMesaId());

        CartaPublicaDto carta = tenantFilterActivator.ejecutarConTenant(tenantId, () ->
                ejecutarConSucursal(sucursalId, () -> {
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
                }));

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
     *
     * 2026-09-10: mismo agregado que obtenerCarta -- se puebla
     * SucursalContext con la sucursal real de la mesa antes de crear el
     * pedido, para que MenuService.obtenerItemDisponibleParaPedido (llamado
     * desde PedidoService.crearPedido) rechace un itemMenuId de OTRA
     * sucursal del mismo tenant con el mismo "no encontrado" generico que
     * ya usa para tenant -- sin este cambio, el endpoint publico aceptaria
     * items de cualquier sucursal del restaurante, no solo la de esta mesa.
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

        Long sucursalId = mesaService.obtenerSucursalIdDeMesa(sesion.getMesaId());

        PedidoDto dto = tenantFilterActivator.ejecutarConTenant(tenantId, () ->
                ejecutarConSucursal(sucursalId, () -> {
                    Pedido pedido = pedidoService.crearPedido(
                            sesion.getMesaId(), request.claveIdempotencia(), request.items(),
                            request.notas(), request.comensal());
                    List<PedidoItemDto> items = pedidoService.listarItems(pedido.getId()).stream()
                            .map(PedidoItemDto::desde)
                            .toList();
                    return PedidoDto.desde(pedido, items);
                }));

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Mismo patron que TenantFilterActivator.ejecutarConTenant, pero para
     * SucursalContext -- deliberadamente NO se agrega a esa clase (que es
     * código de tenant ya verificado; ver su propio javadoc sobre por qué
     * no se toca) ni se reutiliza JwtAuthenticationFilter. Aquí sucursalId
     * siempre viene de una mesa real y nunca es null, así que a
     * MenuService le basta con SucursalContext.obtenerSucursalId() -- no
     * hace falta poblar requiereSeleccion() (esa distinción es solo para
     * el camino autenticado de staff, ver SucursalContext).
     */
    private <T> T ejecutarConSucursal(Long sucursalId, Supplier<T> accion) {
        SucursalContext.establecerSucursalId(sucursalId);
        try {
            return accion.get();
        } finally {
            SucursalContext.limpiar();
        }
    }
}
