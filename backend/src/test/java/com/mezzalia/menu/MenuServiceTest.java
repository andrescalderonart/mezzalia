package com.mezzalia.menu;

import com.mezzalia.menu.excepcion.MenuExceptions;
import com.mezzalia.menu.servicio.MenuService;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No activa el filtro de Hibernate a propósito (a diferencia de
 * AislamientoTenantTest): el objetivo acá es probar la defensa EXPLÍCITA de
 * MenuService (comparar tenant_id contra TenantContext), que existe
 * precisamente porque no hay que asumir que @Filter de Hibernate protege una
 * carga por id (EntityManager.find/Session.get) igual que protege una query
 * HQL/Criteria normal. Ver el comentario en
 * MenuService.perteneceAlTenantActual(...).
 *
 * MenuService se instancia a mano (new) en vez de @Autowired porque
 * @DataJpaTest solo wirea el slice de JPA (repositorios, EntityManager), no
 * @Service beans -- sus @Transactional quedan como metadata inerte acá, pero
 * el propio @DataJpaTest ya envuelve cada test en una transacción que hace
 * rollback al final, así que la persistencia real se prueba igual.
 */
@DataJpaTest
class MenuServiceTest {

    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private CategoriaMenuRepository categoriaMenuRepository;
    @Autowired
    private ItemMenuRepository itemMenuRepository;
    @Autowired
    private HistorialPrecioItemRepository historialPrecioItemRepository;

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
        SecurityContextHolder.clearContext();
    }

    @Test
    void unTenantNoPuedeEditarUnItemDeOtroTenantAunqueAdivineElId() {
        MenuService menuService = nuevoMenuService();

        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante B"));

        autenticarComo("admin.a");
        TenantContext.establecerTenantId(tenantA.getId());
        CategoriaMenu categoriaA = menuService.crearCategoria("Entradas", 0);
        ItemMenu itemA = menuService.crearItem(
                categoriaA.getId(), "Ceviche", "Ceviche de camarón", new BigDecimal("35000"), null);
        testEntityManager.flush();
        testEntityManager.clear();

        // Ahora "entra" un admin del tenant B e intenta tocar el item de A por id.
        autenticarComo("admin.b");
        TenantContext.establecerTenantId(tenantB.getId());

        assertThatThrownBy(() ->
                menuService.actualizarItem(itemA.getId(), "Hackeado", "desc", new BigDecimal("1"), null))
                .isInstanceOf(MenuExceptions.ItemMenuNoEncontradoException.class);
    }

    @Test
    void cambiarPrecioDeUnItemRegistraHistorialConUsuarioYValores() {
        MenuService menuService = nuevoMenuService();

        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Historial"));
        autenticarComo("admin.demo");
        TenantContext.establecerTenantId(tenant.getId());

        CategoriaMenu categoria = menuService.crearCategoria("Fuertes", 0);
        ItemMenu item = menuService.crearItem(categoria.getId(), "Bandeja paisa", null, new BigDecimal("28000"), null);
        testEntityManager.flush();

        menuService.actualizarItem(item.getId(), "Bandeja paisa", "actualizada", new BigDecimal("32000"), null);
        testEntityManager.flush();
        testEntityManager.clear();

        List<HistorialPrecioItem> historial =
                historialPrecioItemRepository.findByItemIdOrderByModificadoEnDesc(item.getId());

        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).getUsuario()).isEqualTo("admin.demo");
        assertThat(historial.get(0).getPrecioAnterior()).isEqualByComparingTo("28000");
        assertThat(historial.get(0).getPrecioNuevo()).isEqualByComparingTo("32000");
    }

    @Test
    void actualizarSinCambiarElPrecioNoEscribeHistorial() {
        MenuService menuService = nuevoMenuService();

        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante SinCambio"));
        autenticarComo("admin.demo");
        TenantContext.establecerTenantId(tenant.getId());

        CategoriaMenu categoria = menuService.crearCategoria("Entradas", 0);
        ItemMenu item = menuService.crearItem(categoria.getId(), "Sopa", null, new BigDecimal("15000"), null);
        testEntityManager.flush();

        menuService.actualizarItem(item.getId(), "Sopa de mariscos", "nueva desc", new BigDecimal("15000"), null);
        testEntityManager.flush();

        assertThat(historialPrecioItemRepository.findByItemIdOrderByModificadoEnDesc(item.getId())).isEmpty();
    }

    @Test
    void eliminarItemEsSoftDeleteYDejaDeListarse() {
        MenuService menuService = nuevoMenuService();

        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Delete"));
        autenticarComo("admin.demo");
        TenantContext.establecerTenantId(tenant.getId());

        CategoriaMenu categoria = menuService.crearCategoria("Postres", 0);
        ItemMenu item = menuService.crearItem(categoria.getId(), "Flan", null, new BigDecimal("9000"), null);
        testEntityManager.flush();

        menuService.eliminarItem(item.getId());
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(menuService.listarItemsDeCategoria(categoria.getId())).isEmpty();
        assertThat(itemMenuRepository.findById(item.getId())).isPresent();
        assertThat(itemMenuRepository.findById(item.getId()).get().isActivo()).isFalse();
    }

    @Test
    void noSePuedeCrearUnItemConPrecioNegativo() {
        MenuService menuService = nuevoMenuService();

        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Precio"));
        autenticarComo("admin.demo");
        TenantContext.establecerTenantId(tenant.getId());
        CategoriaMenu categoria = menuService.crearCategoria("Bebidas", 0);

        assertThatThrownBy(() ->
                menuService.crearItem(categoria.getId(), "Gaseosa", null, new BigDecimal("-1"), null))
                .isInstanceOf(MenuExceptions.PrecioInvalidoException.class);
    }

    @Test
    void unSuperAdminSinTenantEnContextoNoPuedeCrearCategorias() {
        MenuService menuService = nuevoMenuService();
        autenticarComo("superadmin");
        // A propósito NO se llama TenantContext.establecerTenantId(...): así
        // llega un SUPER_ADMIN real (JwtAuthenticationFilter nunca activa
        // TenantContext para ese rol, ver UsuarioInterno).

        assertThatThrownBy(() -> menuService.crearCategoria("Lo que sea", 0))
                .isInstanceOf(MenuExceptions.SinTenantEnContextoException.class);
    }

    private MenuService nuevoMenuService() {
        // AlmacenamientoImagenService en null a propósito: ningún test de
        // esta clase ejerce subida de imágenes.
        return new MenuService(categoriaMenuRepository, itemMenuRepository, historialPrecioItemRepository, null);
    }

    private void autenticarComo(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private Tenant nuevoTenant(String nombre) {
        Tenant tenant = new Tenant();
        tenant.setNombre(nombre);
        tenant.setPlan("STARTER");
        tenant.setMoneda("COP");
        tenant.setZonaHoraria("America/Bogota");
        return tenant;
    }
}
