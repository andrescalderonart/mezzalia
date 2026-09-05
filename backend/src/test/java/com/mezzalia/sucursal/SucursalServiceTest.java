package com.mezzalia.sucursal;

import com.mezzalia.sucursal.excepcion.SucursalExceptions;
import com.mezzalia.sucursal.servicio.SucursalService;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Primera cobertura de SucursalService (cero tests existian antes de este
 * archivo, pese a que MesaService ya depende de el desde este mismo
 * branch). Mismo patron que MesaServiceTest: @DataJpaTest, servicio
 * instanciado a mano.
 *
 * Foco: los tres invariantes de negocio que un test de MesaService NUNCA
 * va a ejercer porque solo llama al "camino feliz" de
 * obtenerSucursalActivaDelTenantActual:
 *  1. Aislamiento de tenant explicito en obtenerSucursal/listarSucursales
 *     (independiente del filtro de Hibernate, igual que en MesaService).
 *  2. "No encontrada" generico para una sucursal inactiva -- no distingue
 *     de un id inexistente, mismo criterio que el resto del sistema.
 *  3. El guardia nuevo de desactivarSucursal: no se puede dejar a un
 *     tenant sin ninguna sucursal activa (ver SucursalExceptions.
 *     UltimaSucursalActivaException) -- sin este test, ese guardia queda
 *     escrito pero nunca ejercido.
 */
@DataJpaTest
class SucursalServiceTest {

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private SucursalRepository sucursalRepository;

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
    }

    @Test
    void listarSucursalesSinTenantEnContextoLanzaExcepcion() {
        SucursalService sucursalService = nuevoSucursalService();

        assertThatThrownBy(sucursalService::listarSucursales)
                .isInstanceOf(SucursalExceptions.SinTenantEnContextoException.class);
    }

    @Test
    void crearSucursalSinTenantEnContextoLanzaExcepcion() {
        SucursalService sucursalService = nuevoSucursalService();

        assertThatThrownBy(() -> sucursalService.crearSucursal("Sede Norte", null))
                .isInstanceOf(SucursalExceptions.SinTenantEnContextoException.class);
    }

    @Test
    void unTenantNuncaVeSucursalesDeOtroTenant() {
        SucursalService sucursalService = nuevoSucursalService();
        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante B"));

        TenantContext.establecerTenantId(tenantA.getId());
        sucursalService.crearSucursal("Sede A1", null);

        TenantContext.establecerTenantId(tenantB.getId());
        sucursalService.crearSucursal("Sede B1", null);

        List<Sucursal> sucursalesDeA;
        TenantContext.establecerTenantId(tenantA.getId());
        sucursalesDeA = sucursalService.listarSucursales();

        assertThat(sucursalesDeA).extracting(Sucursal::getNombre).containsExactly("Sede A1");
    }

    @Test
    void obtenerSucursalActivaDeOtroTenantLanzaNoEncontradaGenerica() {
        SucursalService sucursalService = nuevoSucursalService();
        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante Dueno"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante Intruso"));

        TenantContext.establecerTenantId(tenantA.getId());
        Sucursal sucursalDeA = sucursalService.crearSucursal("Sede Confidencial", null);

        TenantContext.establecerTenantId(tenantB.getId());
        assertThatThrownBy(() -> sucursalService.obtenerSucursalActivaDelTenantActual(sucursalDeA.getId()))
                .isInstanceOf(SucursalExceptions.SucursalNoEncontradaException.class);
    }

    @Test
    void unaSucursalDesactivadaNoSeConsideraActivaAunSiendoDelTenantCorrecto() {
        SucursalService sucursalService = nuevoSucursalService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Dos Sedes"));
        TenantContext.establecerTenantId(tenant.getId());
        Sucursal principal = sucursalService.crearSucursal("Principal", null);
        Sucursal secundaria = sucursalService.crearSucursal("Secundaria", null);

        sucursalService.desactivarSucursal(secundaria.getId());

        assertThatThrownBy(() -> sucursalService.obtenerSucursalActivaDelTenantActual(secundaria.getId()))
                .isInstanceOf(SucursalExceptions.SucursalNoEncontradaException.class);
        assertThat(sucursalService.obtenerSucursalActivaDelTenantActual(principal.getId())).isNotNull();
    }

    @Test
    void noSePuedeDesactivarLaUnicaSucursalActivaDelTenant() {
        SucursalService sucursalService = nuevoSucursalService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Una Sede"));
        TenantContext.establecerTenantId(tenant.getId());
        Sucursal unica = sucursalService.crearSucursal("Unica", null);

        assertThatThrownBy(() -> sucursalService.desactivarSucursal(unica.getId()))
                .isInstanceOf(SucursalExceptions.UltimaSucursalActivaException.class);

        assertThat(sucursalService.obtenerSucursalActivaDelTenantActual(unica.getId())).isNotNull();
    }

    @Test
    void siHayMasDeUnaActivaSiSePuedeDesactivarUna() {
        SucursalService sucursalService = nuevoSucursalService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Dos Sedes Bis"));
        TenantContext.establecerTenantId(tenant.getId());
        Sucursal principal = sucursalService.crearSucursal("Principal", null);
        Sucursal secundaria = sucursalService.crearSucursal("Secundaria", null);

        sucursalService.desactivarSucursal(secundaria.getId());

        assertThat(sucursalService.listarSucursales()).extracting(Sucursal::getId)
                .containsExactly(principal.getId());
    }

    @Test
    void reactivarSucursalLaVuelveAMostrarEnElListado() {
        SucursalService sucursalService = nuevoSucursalService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Reactivacion"));
        TenantContext.establecerTenantId(tenant.getId());
        Sucursal principal = sucursalService.crearSucursal("Principal", null);
        Sucursal secundaria = sucursalService.crearSucursal("Secundaria", null);
        sucursalService.desactivarSucursal(secundaria.getId());

        sucursalService.reactivarSucursal(secundaria.getId());

        assertThat(sucursalService.listarSucursales()).extracting(Sucursal::getId)
                .containsExactlyInAnyOrder(principal.getId(), secundaria.getId());
    }

    private SucursalService nuevoSucursalService() {
        return new SucursalService(sucursalRepository);
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
