package com.mezzalia.common.seguridad;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.mezzalia.tenant.TenantContext;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auditoria 2026-09-08, hallazgo §1.1 (el de mayor prioridad de todo el
 * informe, ver docs/auditoria-tecnica-2026-09-08.md): antes de este test no
 * existia NINGUNA prueba automatizada de que los @PreAuthorize de los
 * controllers admin realmente bloquean algo. Es decir: alguien podia borrar
 * "@PreAuthorize("hasRole('ADMIN_RESTAURANTE')")" de un endpoint entero (por
 * accidente, en un merge, refactor, lo que sea) y ningun test se rompia.
 * Toda la autorizacion del panel dependia, en la practica, de que un humano
 * la revisara a ojo en cada PR.
 *
 * Que se prueba: que un MESERO recibe 403 en todo endpoint restringido a
 * ADMIN_RESTAURANTE, y que un rol distinto de SUPER_ADMIN recibe 403 en todo
 * endpoint bajo /api/admin/tenants/** y /api/superadmin/** -- exactamente
 * los dos limites de rol que hoy existen en el sistema (ver RolUsuario). No
 * se prueba el "camino feliz" (que ADMIN_RESTAURANTE/SUPER_ADMIN SI puedan
 * usar cada endpoint): eso ya lo ejercitan, indirectamente, los tests de
 * servicio existentes (UsuarioInternoAdminServiceTest, etc.) y la app en uso
 * real; agregarlo aca solo para no dejarlo "sin cubrir" infla este archivo
 * sin sumar proteccion nueva contra el riesgo real (una regresion de
 * seguridad que abre algo que deberia estar cerrado, no uno que lo cierra de
 * mas).
 *
 * Por que @SpringBootTest con contexto completo y no @WebMvcTest: probar
 * @PreAuthorize de verdad exige correr la cadena de filtros real que arma
 * SecurityConfig (incluido JwtAuthenticationFilter), y @WebMvcTest NO la
 * arma por default -- solo escanea @Controller/@ControllerAdvice y
 * componentes web, no @Configuration propias como SecurityConfig, salvo que
 * se importen a mano. Importarla a mano para esto choca ademas con que
 * JwtAuthenticationFilter declara "@PersistenceContext EntityManager", que
 * necesita el autoconfigure de JPA que un WebMvcTest excluye por diseño (la
 * inyeccion del EntityManager falla al armar el contexto, no en tiempo de
 * request). El contexto completo evita ese problema de raiz y, de paso,
 * evita tener que mockear cada Service de cada controller (MenuService,
 * MesaService, TenantService, etc.) solo para poder levantar el contexto --
 * cosa que @PreAuthorize hace innecesaria de todos modos: si el rol no
 * alcanza, el metodo del controller (y por lo tanto el Service real) nunca
 * llega a ejecutarse, asi que no importa que las tablas de H2 esten vacias.
 *
 * @WithMockUser no manda header Authorization -- JwtAuthenticationFilter ni
 * siquiera entra a su bloque principal (ver el "if (header != null...)" ahi)
 * para estos requests, así que las autoridades que cuentan son unicamente
 * las que arma @WithMockUser(roles = ...), que Spring Security antepone con
 * "ROLE_" automaticamente -- el mismo prefijo que ya usa
 * JwtAuthenticationFilter.doFilterInternal en produccion (ROLE_ + rol.name()),
 * asi que el mock reproduce fielmente la forma real de la autoridad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AutorizacionAdminTest {

    @Autowired
    private MockMvc mockMvc;

    // Cualquier valor sirve: la base H2 de test arranca vacia en cada clase
    // (ddl-auto: create-drop) y ninguno de estos tests le pide al servicio
    // que devuelva datos reales de un tenant especifico -- solo que no
    // rechace la request por "no hay tenant en contexto". No confundir con
    // un id que exista en la base: no hace falta que exista.
    private static final Long TENANT_ID_DE_PRUEBA = 1L;

    /**
     * Endpoints de UsuarioInternoAdminController, MenuAdminController,
     * MesaAdminController, SucursalAdminController y ComensalAdminController
     * restringidos a ADMIN_RESTAURANTE (via @PreAuthorize de clase o de
     * metodo, ver cada controller). Un MESERO autenticado debe recibir 403
     * en todos.
     */
    @Nested
    class RestringidoAAdminRestaurante {

        @ParameterizedTest(name = "{0} {1} -> 403 para MESERO")
        @MethodSource("com.mezzalia.common.seguridad.AutorizacionAdminTest#endpointsSoloAdminRestaurante")
        @WithMockUser(roles = "MESERO")
        void meseroRecibeProhibido(String metodo, String url, String cuerpoJson) throws Exception {
            mockMvc.perform(construirRequest(metodo, url, cuerpoJson))
                    .andExpect(status().isForbidden());
        }

        /**
         * POST /api/admin/menu/items/{id}/imagen es multipart, no encaja en
         * construirRequest(...) (que solo arma JSON) -- se prueba aparte en
         * vez de forzarlo dentro del MethodSource de arriba. Mismo hallazgo
         * que el resto de este controller: es un endpoint de escritura de la
         * carta, exclusivo de ADMIN_RESTAURANTE.
         */
        @org.junit.jupiter.api.Test
        @WithMockUser(roles = "MESERO")
        void meseroNoPuedeSubirImagenDeItem() throws Exception {
            org.springframework.mock.web.MockMultipartFile archivo =
                    new org.springframework.mock.web.MockMultipartFile(
                            "archivo", "plato.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/api/admin/menu/items/1/imagen")
                            .file(archivo))
                    .andExpect(status().isForbidden());
        }
    }

    /**
     * Contraparte de RestringidoAAdminRestaurante: los GET de menu/mesas/
     * sucursales son a proposito de cualquier usuario autenticado del
     * tenant (ver el javadoc de cada controller -- un MESERO los necesita
     * para tomar pedidos). Si esto empezara a devolver 403 seria una
     * regresion funcional, no una mejora de seguridad -- por eso se prueba
     * tambien el otro sentido, no solo "todo lo que debe estar cerrado".
     */
    @Nested
    class AccesibleParaCualquierAutenticado {

        /**
         * Primera corrida real en CI (2026-09-08): estos SI llegan a
         * ejecutar el metodo real del controller/service -- no los bloquea
         * ningun @PreAuthorize -- y esos servicios llaman a
         * TenantContext.obtenerTenantId() para resolver el tenant. En
         * produccion eso lo puebla JwtAuthenticationFilter a partir del
         * claim del JWT real, pero @WithMockUser no manda ningun header
         * Authorization, asi que sin establecerlo a mano el servicio ve el
         * contexto vacio y responde 403 (SinTenantEnContextoException de
         * cada modulo) por una razon completamente distinta a la que este
         * test quiere probar -- asi fallaron los 4 casos la primera vez que
         * corrio esto en CI. Se establece igual que lo haria el filtro real
         * para MESERO/ADMIN_RESTAURANTE (a diferencia de SUPER_ADMIN,
         * siempre tienen tenantId): MockMvc en modo MOCK corre sincronico en
         * el mismo hilo del test, asi que el ThreadLocal llega intacto al
         * service sin necesidad de nada mas elaborado.
         */
        @ParameterizedTest(name = "{0} {1} -> NO 403/401 para MESERO")
        @MethodSource("com.mezzalia.common.seguridad.AutorizacionAdminTest#endpointsLecturaCompartida")
        @WithMockUser(roles = "MESERO")
        void meseroNoRecibeProhibido(String metodo, String url) throws Exception {
            TenantContext.establecerTenantId(TENANT_ID_DE_PRUEBA);
            try {
                int status = mockMvc.perform(construirRequest(metodo, url, null))
                        .andReturn().getResponse().getStatus();
                org.assertj.core.api.Assertions.assertThat(status)
                        .as("MESERO deberia poder llamar a %s %s sin que el rol se lo bloquee (403) ni pedirle login (401)", metodo, url)
                        .isNotIn(401, 403);
            } finally {
                TenantContext.limpiar();
            }
        }
    }

    /**
     * Endpoints exclusivos de SUPER_ADMIN: todo /api/admin/tenants/** (via
     * @PreAuthorize de clase en TenantAdminController) y todo
     * /api/superadmin/** (via @PreAuthorize de clase en
     * ComensalMantenimientoController, mas la restriccion redundante a nivel
     * de ruta en SecurityConfig -- "doble defensa", ver el javadoc de esa
     * clase). Se prueba con ADMIN_RESTAURANTE (el rol "fuerte" del tenant,
     * el caso mas facil de confundir con SUPER_ADMIN por error) y con
     * MESERO.
     */
    @Nested
    class RestringidoASuperAdmin {

        @ParameterizedTest(name = "{0} {1} -> 403 para ADMIN_RESTAURANTE")
        @MethodSource("com.mezzalia.common.seguridad.AutorizacionAdminTest#endpointsSoloSuperAdmin")
        @WithMockUser(roles = "ADMIN_RESTAURANTE")
        void adminRestauranteRecibeProhibido(String metodo, String url, String cuerpoJson) throws Exception {
            mockMvc.perform(construirRequest(metodo, url, cuerpoJson))
                    .andExpect(status().isForbidden());
        }

        @ParameterizedTest(name = "{0} {1} -> 403 para MESERO")
        @MethodSource("com.mezzalia.common.seguridad.AutorizacionAdminTest#endpointsSoloSuperAdmin")
        @WithMockUser(roles = "MESERO")
        void meseroRecibeProhibido(String metodo, String url, String cuerpoJson) throws Exception {
            mockMvc.perform(construirRequest(metodo, url, cuerpoJson))
                    .andExpect(status().isForbidden());
        }
    }

    /**
     * Sin @WithMockUser (sin autenticar) en una muestra representativa de un
     * endpoint por controller admin: confirma que SecurityConfig sigue
     * exigiendo autenticacion (401, con el cuerpo JSON del
     * accessDeniedHandler/authenticationEntryPoint de SecurityConfig, no un
     * 403/500 crudo del servlet container) para /api/admin/** en general, no
     * solo para los que ademas llevan @PreAuthorize de rol.
     */
    @Nested
    class SinAutenticar {

        @ParameterizedTest(name = "{0} {1} -> 401 sin autenticar")
        @MethodSource("com.mezzalia.common.seguridad.AutorizacionAdminTest#endpointsMuestraSinAutenticar")
        void requestSinTokenRecibeNoAutenticado(String metodo, String url) throws Exception {
            mockMvc.perform(construirRequest(metodo, url, null))
                    .andExpect(status().isUnauthorized());
        }
    }

    private static MockHttpServletRequestBuilder construirRequest(String metodo, String url, String cuerpoJson) {
        MockHttpServletRequestBuilder builder = switch (metodo) {
            case "GET" -> get(url);
            case "POST" -> post(url);
            case "PUT" -> put(url);
            case "PATCH" -> patch(url);
            case "DELETE" -> delete(url);
            default -> throw new IllegalArgumentException("Metodo HTTP no soportado en este test: " + metodo);
        };
        if (cuerpoJson != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(cuerpoJson);
        }
        return builder;
    }

    static Stream<Arguments> endpointsSoloAdminRestaurante() {
        return Stream.of(
                // UsuarioInternoAdminController: @PreAuthorize a nivel de clase, todo el controller.
                Arguments.of("GET", "/api/admin/usuarios", null),
                Arguments.of("POST", "/api/admin/usuarios", "{\"username\":\"nuevo.mesero\",\"password\":\"password123\",\"rol\":\"MESERO\"}"),
                Arguments.of("PATCH", "/api/admin/usuarios/1/desactivar", null),
                Arguments.of("PATCH", "/api/admin/usuarios/1/reactivar", null),
                Arguments.of("PATCH", "/api/admin/usuarios/1/password", "{\"passwordNueva\":\"otraPassword123\"}"),
                Arguments.of("GET", "/api/admin/usuarios/1/sucursales", null),
                Arguments.of("PUT", "/api/admin/usuarios/1/sucursales/1", null),
                Arguments.of("DELETE", "/api/admin/usuarios/1/sucursales/1", null),
                Arguments.of("GET", "/api/admin/usuarios/1/turnos", null),

                // MenuAdminController: solo los endpoints de escritura llevan @PreAuthorize de metodo.
                Arguments.of("POST", "/api/admin/menu/categorias", "{\"nombre\":\"Bebidas\",\"orden\":1}"),
                Arguments.of("POST", "/api/admin/menu/items", "{\"categoriaId\":1,\"nombre\":\"Item\",\"precio\":10.5}"),
                Arguments.of("PUT", "/api/admin/menu/items/1", "{\"nombre\":\"Item\",\"precio\":10.5}"),
                Arguments.of("PATCH", "/api/admin/menu/items/1/disponibilidad", "{\"disponible\":true}"),
                Arguments.of("DELETE", "/api/admin/menu/items/1", null),
                // Editor de ingredientes (2026-11-26)
                Arguments.of("GET", "/api/admin/menu/items/1/ingredientes", null),
                Arguments.of("POST", "/api/admin/menu/items/1/ingredientes", "{\"nombre\":\"Cebolla\"}"),
                Arguments.of("PUT", "/api/admin/menu/items/1/ingredientes/orden", "{\"ids\":[1]}"),
                Arguments.of("PATCH", "/api/admin/menu/items/1/presentacion", "{\"presentacion\":\"APILADO\"}"),
                Arguments.of("PUT", "/api/admin/menu/ingredientes/1", "{\"nombre\":\"Cebolla\",\"removible\":true}"),
                Arguments.of("DELETE", "/api/admin/menu/ingredientes/1", null),
                // Reportes del negocio (2026-11-27)
                Arguments.of("GET", "/api/admin/reportes/ingredientes-quitados", null),

                // MesaAdminController: idem, solo escritura.
                Arguments.of("POST", "/api/admin/mesas", "{\"codigo\":\"M1\",\"sucursalId\":1}"),
                Arguments.of("DELETE", "/api/admin/mesas/1", null),
                Arguments.of("POST", "/api/admin/mesas/1/sesion", null),

                // SucursalAdminController: escritura + el GET de detalle de metodo (ver comentario en el controller).
                Arguments.of("POST", "/api/admin/sucursales", "{\"nombre\":\"Sucursal Centro\"}"),
                Arguments.of("PATCH", "/api/admin/sucursales/1/desactivar", null),
                Arguments.of("PATCH", "/api/admin/sucursales/1/reactivar", null),
                Arguments.of("GET", "/api/admin/sucursales/1/usuarios", null),

                // ComensalAdminController: unico endpoint, borrado de datos de comensal.
                Arguments.of("DELETE", "/api/admin/comensales/1", null)
        );
    }

    static Stream<Arguments> endpointsLecturaCompartida() {
        return Stream.of(
                Arguments.of("GET", "/api/admin/menu/categorias"),
                Arguments.of("GET", "/api/admin/menu/categorias/1/items"),
                Arguments.of("GET", "/api/admin/mesas"),
                Arguments.of("GET", "/api/admin/sucursales")
        );
    }

    static Stream<Arguments> endpointsSoloSuperAdmin() {
        return Stream.of(
                // TenantAdminController: @PreAuthorize a nivel de clase + ademas restringido
                // por ruta en SecurityConfig ("/api/admin/tenants/**").
                Arguments.of("GET", "/api/admin/tenants", null),
                Arguments.of("POST", "/api/admin/tenants", "{\"nombre\":\"Tenant Test\",\"plan\":\"STARTER\",\"moneda\":\"COP\",\"zonaHoraria\":\"America/Bogota\"}"),
                Arguments.of("PATCH", "/api/admin/tenants/1/branding", "{}"),
                Arguments.of("PATCH", "/api/admin/tenants/1/suspender", null),
                Arguments.of("PATCH", "/api/admin/tenants/1/reactivar", null),

                // TenantAdminInicialController: mismo prefijo de ruta que TenantAdminController
                // pero vive en el modulo identidad (ver su javadoc para el porque).
                Arguments.of("POST", "/api/admin/tenants/1/admin-inicial", "{\"username\":\"nuevo.admin\",\"password\":\"password123\"}"),

                // ComensalMantenimientoController: idem, bajo /api/superadmin/**.
                Arguments.of("POST", "/api/superadmin/comensales/reencriptar", null)
        );
    }

    static Stream<Arguments> endpointsMuestraSinAutenticar() {
        return Stream.of(
                Arguments.of("GET", "/api/admin/usuarios"),
                Arguments.of("GET", "/api/admin/menu/categorias"),
                Arguments.of("GET", "/api/admin/tenants")
        );
    }
}
