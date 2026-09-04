package com.mezzalia.pedido.controlador;

import com.mezzalia.pedido.servicio.PedidoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §10: solo ADMIN_RESTAURANTE ve el telefono completo del comensal. Este
 * test cubre unicamente esa decision de rol -- PedidoService no se llama
 * desde telefonoCompleto(), asi que se instancia el controlador con null
 * en vez de levantar todas las dependencias reales de PedidoService (mismo
 * criterio de "probar solo lo que tiene logica" que ComensalDtoTest).
 */
class PedidoAdminControllerTest {

    private final PedidoAdminController controller = new PedidoAdminController((PedidoService) null);

    @AfterEach
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(String rol) {
        var authority = new SimpleGrantedAuthority("ROLE_" + rol);
        var auth = new UsernamePasswordAuthenticationToken("usuario-test", null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void adminRestauranteVeElTelefonoCompleto() {
        autenticarComo("ADMIN_RESTAURANTE");

        assertThat(controller.telefonoCompleto()).isTrue();
    }

    @Test
    void meseroNoVeElTelefonoCompleto() {
        autenticarComo("MESERO");

        assertThat(controller.telefonoCompleto()).isFalse();
    }

    @Test
    void sinAutenticacionElDefaultEsSeguro() {
        // SecurityContextHolder ya limpio (ver @AfterEach de tests anteriores
        // y estado inicial de la clase): sin Authentication en contexto.
        assertThat(controller.telefonoCompleto()).isFalse();
    }
}
