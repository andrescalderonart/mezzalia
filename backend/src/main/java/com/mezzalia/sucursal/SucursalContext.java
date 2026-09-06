package com.mezzalia.sucursal;

/**
 * RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §4): almacena, usando
 * ThreadLocal, la sucursal en la que el usuario autenticado del request en
 * curso eligio trabajar -- MISMO mecanismo y MISMO ciclo de vida que
 * tenant.TenantContext (poblado por JwtAuthenticationFilter al validar el
 * JWT, limpiado siempre en su finally), pero deliberadamente una clase
 * aparte en vez de agregarle un segundo campo a TenantContext: el limite de
 * tenant es absoluto (nadie lo cruza nunca) mientras que el de sucursal es
 * cruzable a proposito por ADMIN_RESTAURANTE -- mezclarlos en una sola
 * clase invitaria a tratarlos con la misma semantica, que es exactamente lo
 * que la propuesta (§4) dice que NO hay que hacer (ver el bug real de
 * SUPER_ADMIN sobre listarMesas/listarPedidos que un filtro global
 * incondicional ya causo una vez en este proyecto).
 *
 * requiereSeleccion() es el dato que distingue los dos "null" que
 * obtenerSucursalId() puede devolver, que NO significan lo mismo:
 *  - ADMIN_RESTAURANTE sin sucursal elegida: null legitimo, "ver todas las
 *    sucursales del tenant" (comportamiento identico al que existia antes
 *    de esta rama).
 *  - MESERO sin sucursal elegida (nunca llamo a
 *    SesionController.seleccionarSucursal despues de loguearse, o su
 *    access token vino de un /api/auth/refresh reciente -- ver el
 *    limitante documentado en AuthService.emitirPar): null que un servicio
 *    NUNCA debe interpretar como "ver todas" -- es la fuga exacta que este
 *    mecanismo existe para evitar. Cada servicio de dominio decide que
 *    hacer con esa distincion en su propio metodo "sucursalActual()" (ver
 *    MesaService/PedidoService) -- esta clase solo guarda el dato, no
 *    impone la politica.
 */
public final class SucursalContext {

    private static final ThreadLocal<Long> sucursalIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> requiereSeleccionHolder = ThreadLocal.withInitial(() -> false);

    private SucursalContext() {}

    public static void establecerSucursalId(Long sucursalId) {
        sucursalIdHolder.set(sucursalId);
    }

    public static Long obtenerSucursalId() {
        return sucursalIdHolder.get();
    }

    public static void marcarRequiereSeleccion(boolean requiere) {
        requiereSeleccionHolder.set(requiere);
    }

    public static boolean requiereSeleccion() {
        return requiereSeleccionHolder.get();
    }

    public static void limpiar() {
        sucursalIdHolder.remove();
        requiereSeleccionHolder.remove();
    }
}
