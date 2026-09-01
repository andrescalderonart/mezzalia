package com.mezzalia.menu.controlador;

import com.mezzalia.mesa.SesionMesa;
import com.mezzalia.mesa.excepcion.MesaExceptions;
import com.mezzalia.mesa.servicio.MesaService;
import com.mezzalia.menu.dto.CartaPublicaDto;
import com.mezzalia.menu.dto.CategoriaPublicaDto;
import com.mezzalia.menu.dto.ItemPublicoDto;
import com.mezzalia.menu.servicio.MenuService;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantFilterActivator;
import com.mezzalia.tenant.dto.BrandingDto;
import com.mezzalia.tenant.servicio.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/publico/mesas")
@RequiredArgsConstructor
public class MenuPublicoController {

    private final MesaService mesaService;
    private final MenuService menuService;
    private final TenantService tenantService;
    private final TenantFilterActivator tenantFilterActivator;

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
     * RF-03..RF-07: el módulo `pedidos` (carrito, idempotencia, recálculo de
     * precio en servidor, websocket al panel, captura de datos del comensal
     * bajo Habeas Data para RF-13) todavía no existe -- ver
     * docs/documentacion-tecnica.md §13, Fase 1, y §15.2 (retención de datos
     * del comensal y texto de aviso de privacidad siguen sin resolver, y son
     * trabajo legal, no de arquitectura). 501 explícito en vez de 200 vacío:
     * la versión anterior de este endpoint devolvía 200 OK sin hacer nada,
     * lo cual le habría hecho creer a un comensal real que su pedido se
     * envió correctamente -- eso es peor que no tener el endpoint.
     */
    @PostMapping("/{token}/pedidos")
    public ResponseEntity<Void> crearPedido(@PathVariable String token, @RequestBody Object body) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
