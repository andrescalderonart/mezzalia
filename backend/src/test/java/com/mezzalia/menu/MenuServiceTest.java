package com.mezzalia.menu;

import com.mezzalia.menu.excepcion.MenuExceptions;
import com.mezzalia.menu.servicio.MenuService;
import com.mezzalia.sucursal.SucursalContext;
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
 *
 * 2026-09-11: cada categoria/item ahora exige sucursal seleccionada
 * (MenuService.sucursalActual() nunca acepta null, ver su javadoc), asi que
 * todo test que llegue a crearCategoria()/crearItem() necesita
 * SucursalContext.establecerSucursalId(...) antes -- se usa el placeholder
 * 1L, mismo criterio que Mesa.sucursalId en PedidoServiceTest, porque a
 * estos tests no les importa el valor real, solo que exista. Los tests de
 * unSuperAdminSinTenantEnContextoNoPuedeXxx NO lo necesitan: fallan por
 * SinTenantEnContextoException antes de llegar a sucursalActual() (el chequeo
 * de tenant va primero).
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
        SucursalContext.limpiar();
        SecurityContextHolder.clearContext();
    }

    @Test
    void unTenantNoPuedeEditarUnItemDeOtroTenantAunqueAdivineElId() {
        MenuService menuService = nuevoMenuService();

        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante B"));

        autenticarComo("admin.a");
        TenantContext.establecerTenantId(tenantA.getId());
        SucursalContext.establecerSucursalId(1L);
        CategoriaMenu categoriaA = menuService.crearCategoria("Entradas", 0);
        ItemMenu itemA = menuService.crearItem(
                categoriaA.getId(), "Ceviche", "Ceviche de camarón", new BigDecimal("35000"), null);
        testEntityManager.flush();
        testEntityManager.clear();

        // Ahora "entra" un admin del tenant B e intenta tocar el item de A por id.
        autenticarComo("admin.b");
        TenantContext.establecerTenantId(tenantB.getId());
        SucursalContext.establecerSucursalId(1L);

        assertThatThrownBy(() ->
                menuService.actualizarItem(itemA.getId(), "Hackeado", "desc", new BigDecimal("1"), null))
                .isInstanceOf(MenuExceptions.ItemMenuNoEncontradoException.class);
    }

    /**
     * 2026-09-11: el mismo ataque que arriba pero sin cambiar de tenant --
     * un admin del MISMO restaurante, logueado sobre otra sucursal, intenta
     * tocar un item por id que sabe (o adivina) que pertenece a la carta de
     * otra sucursal. Antes de esta version, MenuService no tenia forma de
     * distinguir esto (solo comparaba tenant_id); hoy obtenerItem() exige
     * tambien perteneceALaSucursalActual(), y esta era la unica prueba que
     * faltaba para confirmarlo (ver MenuService.obtenerItem/obtenerCategoria).
     */
    @Test
    void unaSucursalNoPuedeEditarUnItemDeOtraSucursalDelMismoTenantAunqueAdivineElId() {
        MenuService menuService = nuevoMenuService();

        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Multisucursal Edicion"));
        autenticarComo("admin.multisucursal");
        TenantContext.establecerTenantId(tenant.getId());

        SucursalContext.establecerSucursalId(10L);
        CategoriaMenu categoria = menuService.crearCategoria("Entradas", 0);
        ItemMenu item = menuService.crearItem(
                categoria.getId(), "Ceviche", "Ceviche de camarón", new BigDecimal("35000"), null);
        testEntityManager.flush();
        testEntityManager.clear();

        // Mismo tenant, otra sucursal -- debe resolver "no encontrado",
        // exactamente el mismo criterio que usa este servicio para evitar
        // confirmarle a un atacante que el recurso existe en otro lado.
        SucursalContext.establecerSucursalId(20L);
        assertThatThrownBy(() ->
                menuService.actualizarItem(item.getId(), "Hackeado", "desc", new BigDecimal("1"), null))
                .isInstanceOf(MenuExceptions.ItemMenuNoEncontradoException.class);
    }

    /**
     * 2026-09-11: del lado de lectura -- listarCategorias() debe devolver
     * SOLO la carta de la sucursal seleccionada, nunca la de otra sucursal
     * del mismo tenant mezclada. Es la prueba directa de la decision de
     * producto "menu totalmente independiente por sucursal" (Andres,
     * 2026-09-10): sin esto, nada en el suite confirmaba que las dos cartas
     * de verdad quedan separadas y no solo que "existe una columna
     * sucursal_id".
     */
    @Test
    void listarCategoriasSoloDevuelveLasDeLaSucursalSeleccionada() {
        MenuService menuService = nuevoMenuService();

        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Multisucursal Listado"));
        autenticarComo("admin.multisucursal");
        TenantContext.establecerTenantId(tenant.getId());

        SucursalContext.establecerSucursalId(10L);
        menuService.crearCategoria("Entradas Centro", 0);
        testEntityManager.flush();

        SucursalContext.establecerSucursalId(20L);
        menuService.crearCategoria("Entradas Norte", 0);
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(menuService.listarCategorias())
                .extracting(CategoriaMenu::getNombre)
                .containsExactly("Entradas Norte");

        SucursalContext.establecerSucursalId(10L);
        assertThat(menuService.listarCategorias())
                .extracting(CategoriaMenu::getNombre)
                .containsExactly("Entradas Centro");
    }

    @Test
    void cambiarPrecioDeUnItemRegistraHistorialConUsuarioYValores() {
        MenuService menuService = nuevoMenuService();

        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Historial"));
        autenticarComo("admin.demo");
        TenantContext.establecerTenantId(tenant.getId());
        SucursalContext.establecerSucursalId(1L);

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
        SucursalContext.establecerSucursalId(1L);

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
        SucursalContext.establecerSucursalId(1L);

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
        SucursalContext.establecerSucursalId(1L);
        CategoriaMenu categoria = menuService.crearCategoria("Bebidas", 0);

        assertThatThrownBy(() ->
                menuService.crearItem(categoria.getId(), "Gaseosa", null, new BigDecimal("-1"), null))
                .isInstanceOf(MenuExceptions.PrecioInvalidoException.class);
    }

    /**
     * 2026-09-11: camino feliz de actualizarModelo3d -- ambas URLs se
     * guardan, y null+null en una llamada posterior las limpia (quitarle
     * el modelo 3D a un item que ya lo tenía es un caso válido, no un
     * error).
     */
    @Test
    void actualizarModelo3dConAmbasUrlsLasGuardaYConNullYNullLasLimpia() {
        MenuService menuService = nuevoMenuService();

        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Modelo3d"));
        autenticarComo("admin.demo");
        TenantContext.establecerTenantId(tenant.getId());
        SucursalContext.establecerSucursalId(1L);

        CategoriaMenu categoria = menuService.crearCategoria("Fuertes", 0);
        ItemMenu item = menuService.crearItem(categoria.getId(), "Bandeja paisa", null, new BigDecimal("28000"), null);
        testEntityManager.flush();

        ItemMenu actualizado = menuService.actualizarModelo3d(
                item.getId(), "https://cdn.ejemplo.com/bandeja.glb", "https://cdn.ejemplo.com/bandeja.usdz");
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(itemMenuRepository.findById(item.getId()).orElseThrow().getModeloGlbUrl())
                .isEqualTo("https://cdn.ejemplo.com/bandeja.glb");
        assertThat(actualizado.getModeloUsdzUrl()).isEqualTo("https://cdn.ejemplo.com/bandeja.usdz");

        menuService.actualizarModelo3d(item.getId(), null, null);
        testEntityManager.flush();
        testEntityManager.clear();

        ItemMenu limpio = itemMenuRepository.findById(item.getId()).orElseThrow();
        assertThat(limpio.getModeloGlbUrl()).isNull();
        assertThat(limpio.getModeloUsdzUrl()).isNull();
    }

    /**
     * 2026-09-11: un modelo 3D con una sola URL no sirve -- confirmaria en
     * la carta publica un boton "Ver en 3D" que solo funciona en un sistema
     * operativo (ver el javadoc de MenuService.actualizarModelo3d). Ninguna
     * de las dos combinaciones a medias debe persistirse.
     */
    @Test
    void actualizarModelo3dConUnaSolaUrlLanzaExcepcionYNoPersisteNada() {
        MenuService menuService = nuevoMenuService();

        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Modelo3dIncompleto"));
        autenticarComo("admin.demo");
        TenantContext.establecerTenantId(tenant.getId());
        SucursalContext.establecerSucursalId(1L);

        CategoriaMenu categoria = menuService.crearCategoria("Fuertes", 0);
        ItemMenu item = menuService.crearItem(categoria.getId(), "Sancocho", null, new BigDecimal("22000"), null);
        testEntityManager.flush();

        assertThatThrownBy(() ->
                menuService.actualizarModelo3d(item.getId(), "https://cdn.ejemplo.com/sancocho.glb", null))
                .isInstanceOf(MenuExceptions.Modelo3dIncompletoException.class);

        assertThatThrownBy(() ->
                menuService.actualizarModelo3d(item.getId(), null, "https://cdn.ejemplo.com/sancocho.usdz"))
                .isInstanceOf(MenuExceptions.Modelo3dIncompletoException.class);

        testEntityManager.clear();
        ItemMenu sinTocar = itemMenuRepository.findById(item.getId()).orElseThrow();
        assertThat(sinTocar.getModeloGlbUrl()).isNull();
        assertThat(sinTocar.getModeloUsdzUrl()).isNull();
    }

    /**
     * 2026-09-11: mismo ataque que
     * unTenantNoPuedeEditarUnItemDeOtroTenantAunqueAdivineElId, pero contra
     * actualizarModelo3d -- confirma que hereda la defensa de obtenerItem()
     * y no la saltea por ser un metodo nuevo.
     */
    @Test
    void unTenantNoPuedeAsignarleModelo3dAUnItemDeOtroTenantAunqueAdivineElId() {
        MenuService menuService = nuevoMenuService();

        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante Modelo3d A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante Modelo3d B"));

        autenticarComo("admin.a");
        TenantContext.establecerTenantId(tenantA.getId());
        SucursalContext.establecerSucursalId(1L);
        CategoriaMenu categoriaA = menuService.crearCategoria("Entradas", 0);
        ItemMenu itemA = menuService.crearItem(categoriaA.getId(), "Ceviche", null, new BigDecimal("35000"), null);
        testEntityManager.flush();
        testEntityManager.clear();

        autenticarComo("admin.b");
        TenantContext.establecerTenantId(tenantB.getId());
        SucursalContext.establecerSucursalId(1L);

        assertThatThrownBy(() ->
                menuService.actualizarModelo3d(itemA.getId(), "https://cdn.ejemplo.com/x.glb", "https://cdn.ejemplo.com/x.usdz"))
                .isInstanceOf(MenuExceptions.ItemMenuNoEncontradoException.class);
    }

    @Test
    void unSuperAdminSinTenantEnContextoNoPuedeCrearCategorias() {
        MenuService menuService = nuevoMenuService();
        autenticarComo("superadmin");
        // A propósito NO se llama TenantContext.establecerTenantId(...): así
        // llega un SUPER_ADMIN real (JwtAuthenticationFilter nunca activa
        // TenantContext para ese rol, ver UsuarioInterno). Tampoco hace falta
        // SucursalContext aca: tenantActual() se evalua primero y ya lanza.

        assertThatThrownBy(() -> menuService.crearCategoria("Lo que sea", 0))
                .isInstanceOf(MenuExceptions.SinTenantEnContextoException.class);
    }

    @Test
    void unSuperAdminSinTenantEnContextoNoPuedeListarCategorias() {
        MenuService menuService = nuevoMenuService();
        autenticarComo("superadmin");
        // Mismo caso que unSuperAdminSinTenantEnContextoNoPuedeCrearCategorias,
        // pero del lado de lectura: listarCategorias() dependia solo del
        // filtro de Hibernate para el aislamiento -- que tampoco se activa
        // para SUPER_ADMIN -- y devolvia la carta de TODOS los restaurantes.

        assertThatThrownBy(menuService::listarCategorias)
                .isInstanceOf(MenuExceptions.SinTenantEnContextoException.class);
    }

    @Test
    void unSuperAdminSinTenantEnContextoNoPuedeListarItemsDeCategoria() {
        MenuService menuService = nuevoMenuService();
        autenticarComo("superadmin");

        assertThatThrownBy(() -> menuService.listarItemsDeCategoria(999L))
                .isInstanceOf(MenuExceptions.SinTenantEnContextoException.class);
    }

    private MenuService nuevoMenuService() {
        // AlmacenamientoImagenService y AlmacenamientoModelo3dService en
        // null a propósito: ningún test de esta clase ejerce subida de
        // imágenes ni de modelos 3D (esos flujos se prueban por separado,
        // sin @DataJpaTest -- ver AlmacenamientoModelo3dServiceTest).
        return new MenuService(categoriaMenuRepository, itemMenuRepository, historialPrecioItemRepository, null, null);
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
