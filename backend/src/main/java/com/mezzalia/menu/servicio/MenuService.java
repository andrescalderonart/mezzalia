package com.mezzalia.menu.servicio;

import com.mezzalia.menu.CategoriaMenu;
import com.mezzalia.menu.CategoriaMenuRepository;
import com.mezzalia.menu.HistorialPrecioItem;
import com.mezzalia.menu.HistorialPrecioItemRepository;
import com.mezzalia.menu.ItemMenu;
import com.mezzalia.menu.ItemMenuRepository;
import com.mezzalia.menu.excepcion.MenuExceptions;
import com.mezzalia.sucursal.SucursalContext;
import com.mezzalia.sucursal.excepcion.SucursalExceptions;
import com.mezzalia.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final CategoriaMenuRepository categoriaMenuRepository;
    private final ItemMenuRepository itemMenuRepository;
    private final HistorialPrecioItemRepository historialPrecioItemRepository;
    private final AlmacenamientoImagenService almacenamientoImagenService;

    /**
     * Defensa explicita, mismo criterio que crearCategoria()/obtenerItem():
     * este metodo depende del filtro de Hibernate (tenantFilter) para el
     * aislamiento entre tenants, pero ese filtro NUNCA se activa para
     * SUPER_ADMIN (TenantContext vacio a proposito -- ver
     * JwtAuthenticationFilter, "tenantId ausente = SUPER_ADMIN"). Sin este
     * guardia, un token SUPER_ADMIN recibia la carta de TODOS los
     * restaurantes en una sola lista con GET /api/admin/menu/categorias --
     * tenantActual() lanza si no hay contexto, cerrando ese hueco igual que
     * ya cierra el resto de metodos de escritura de este servicio.
     *
     * 2026-09-10: cada sucursal tiene su propia carta (decision de producto),
     * asi que ademas del tenant se exige una sucursal seleccionada -- ver
     * sucursalActual() para por que aca es SIEMPRE obligatoria, a diferencia
     * de MesaService/PedidoService donde ADMIN sin sucursal significa "ver
     * todas".
     */
    @Transactional(readOnly = true)
    public List<CategoriaMenu> listarCategorias() {
        tenantActual();
        Long sucursalId = sucursalActual();
        return categoriaMenuRepository.findBySucursalIdAndActivoTrueOrderByOrdenAsc(sucursalId);
    }

    @Transactional
    public CategoriaMenu crearCategoria(String nombre, int orden) {
        CategoriaMenu categoria = new CategoriaMenu();
        categoria.setTenantId(tenantActual());
        categoria.setSucursalId(sucursalActual());
        categoria.setNombre(nombre);
        categoria.setOrden(orden);
        return categoriaMenuRepository.save(categoria);
    }

    /**
     * Ya estaba protegido indirectamente: obtenerCategoria() exige que la
     * categoria pertenezca al tenant Y a la sucursal del contexto actual, y
     * para SUPER_ADMIN (sin tenant) eso siempre resuelve "no encontrada". Se
     * agrega tenantActual() explicito aca tambien -- mismo criterio del
     * resto de esta clase: no depender solo de que otro metodo interno
     * mantenga la proteccion.
     */
    @Transactional(readOnly = true)
    public List<ItemMenu> listarItemsDeCategoria(Long categoriaId) {
        tenantActual();
        CategoriaMenu categoria = obtenerCategoria(categoriaId);
        return itemMenuRepository.findByCategoriaIdAndSucursalIdAndActivoTrue(categoriaId, categoria.getSucursalId());
    }

    @Transactional
    public ItemMenu crearItem(Long categoriaId, String nombre, String descripcion, BigDecimal precio, String imagenUrl) {
        validarPrecio(precio);
        CategoriaMenu categoria = obtenerCategoria(categoriaId);

        ItemMenu item = new ItemMenu();
        item.setTenantId(tenantActual());
        // sucursalId SIEMPRE se hereda de la categoria, nunca de
        // sucursalActual() por separado -- ver el javadoc de ItemMenu.
        item.setSucursalId(categoria.getSucursalId());
        item.setCategoriaId(categoriaId);
        item.setNombre(nombre);
        item.setDescripcion(descripcion);
        item.setPrecio(precio);
        item.setImagenUrl(imagenUrl);
        return itemMenuRepository.save(item);
    }

    /**
     * RF-12: si el precio cambia, el historial se escribe en la MISMA
     * transacción que el update -- no es un paso aparte que alguien pueda
     * saltarse llamando directo al repositorio.
     */
    @Transactional
    public ItemMenu actualizarItem(Long itemId, String nombre, String descripcion, BigDecimal precio, String imagenUrl) {
        validarPrecio(precio);
        ItemMenu item = obtenerItem(itemId);

        BigDecimal precioAnterior = item.getPrecio();
        boolean cambioPrecio = precioAnterior.compareTo(precio) != 0;

        item.setNombre(nombre);
        item.setDescripcion(descripcion);
        item.setPrecio(precio);
        item.setImagenUrl(imagenUrl);
        itemMenuRepository.save(item);

        if (cambioPrecio) {
            registrarCambioPrecio(item, precioAnterior, precio);
        }
        return item;
    }

    @Transactional
    public ItemMenu cambiarDisponibilidad(Long itemId, boolean disponible) {
        ItemMenu item = obtenerItem(itemId);
        item.setDisponible(disponible);
        return itemMenuRepository.save(item);
    }

    /** Soft delete -- NUNCA borrado físico: un pedido histórico puede seguir
     * referenciando este ítem (documentacion-tecnica.md §8). */
    @Transactional
    public void eliminarItem(Long itemId) {
        ItemMenu item = obtenerItem(itemId);
        item.setActivo(false);
        itemMenuRepository.save(item);
    }

    @Transactional
    public ItemMenu actualizarImagen(Long itemId, MultipartFile archivo) {
        ItemMenu item = obtenerItem(itemId);
        String rutaPublica = almacenamientoImagenService.guardar(archivo);
        item.setImagenUrl(rutaPublica);
        return itemMenuRepository.save(item);
    }

    /**
     * 2026-09-10: copia todas las categorias e items activos de
     * sucursalOrigenId hacia la sucursal actualmente seleccionada
     * (sucursalActual()) -- mitiga el costo de que cada sucursal nueva
     * arranque con la carta vacia (decision de producto: menus totalmente
     * independientes por sucursal, no un maestro con overrides).
     *
     * Rechaza la operacion si la sucursal destino ya tiene carta activa
     * (decision de producto explicita de Andres, 2026-09-10): nunca mezclar
     * ni duplicar encima de una carta existente, el usuario debe vaciarla a
     * mano primero si de verdad quiere reemplazarla.
     *
     * sucursalOrigenId NO se valida por separado contra el tenant actual: la
     * consulta a categoriaMenuRepository ya esta filtrada por el filtro de
     * Hibernate de tenant (esta operacion solo se alcanza autenticado, ver
     * MenuAdminController), asi que si sucursalOrigenId pertenece a otro
     * tenant, simplemente no devuelve categorias y cae en
     * SucursalOrigenSinCartaException -- mismo mensaje generico que "esta
     * vacia", sin confirmarle a nadie que ese id existe en otro
     * restaurante.
     *
     * La imagen de cada item se copia por referencia (mismo imagenUrl) --
     * es la misma foto del plato, no un archivo distinto; si el admin
     * quiere una foto diferente para la sucursal nueva, la reemplaza
     * despues con actualizarImagen().
     */
    @Transactional
    public List<CategoriaMenu> duplicarMenuDesdeSucursal(Long sucursalOrigenId) {
        tenantActual();
        Long sucursalDestino = sucursalActual();

        if (categoriaMenuRepository.existsBySucursalIdAndActivoTrue(sucursalDestino)) {
            throw new MenuExceptions.SucursalDestinoYaTieneCartaException();
        }

        List<CategoriaMenu> categoriasOrigen = categoriaMenuRepository.findBySucursalIdAndActivoTrueOrderByOrdenAsc(sucursalOrigenId);
        if (categoriasOrigen.isEmpty()) {
            throw new MenuExceptions.SucursalOrigenSinCartaException();
        }

        List<CategoriaMenu> categoriasNuevas = new ArrayList<>();
        for (CategoriaMenu original : categoriasOrigen) {
            CategoriaMenu copia = new CategoriaMenu();
            copia.setTenantId(original.getTenantId());
            copia.setSucursalId(sucursalDestino);
            copia.setNombre(original.getNombre());
            copia.setOrden(original.getOrden());
            copia = categoriaMenuRepository.save(copia);
            categoriasNuevas.add(copia);

            List<ItemMenu> itemsOriginal = itemMenuRepository.findByCategoriaIdAndSucursalIdAndActivoTrue(original.getId(), sucursalOrigenId);
            for (ItemMenu itemOriginal : itemsOriginal) {
                ItemMenu copiaItem = new ItemMenu();
                copiaItem.setTenantId(itemOriginal.getTenantId());
                copiaItem.setSucursalId(copia.getSucursalId());
                copiaItem.setCategoriaId(copia.getId());
                copiaItem.setNombre(itemOriginal.getNombre());
                copiaItem.setDescripcion(itemOriginal.getDescripcion());
                copiaItem.setPrecio(itemOriginal.getPrecio());
                copiaItem.setImagenUrl(itemOriginal.getImagenUrl());
                copiaItem.setDisponible(itemOriginal.isDisponible());
                itemMenuRepository.save(copiaItem);
            }
        }
        return categoriasNuevas;
    }

    private void registrarCambioPrecio(ItemMenu item, BigDecimal anterior, BigDecimal nuevo) {
        HistorialPrecioItem historial = new HistorialPrecioItem();
        historial.setTenantId(item.getTenantId());
        historial.setItemId(item.getId());
        historial.setUsuario(usuarioActual());
        historial.setPrecioAnterior(anterior);
        historial.setPrecioNuevo(nuevo);
        historialPrecioItemRepository.save(historial);
    }

    /**
     * Defensa EXPLÍCITA además del filtro de Hibernate (EntidadConTenant):
     * findById(...) usa internamente EntityManager.find()/Session.get(), y el
     * comportamiento de los @Filter de Hibernate ante una carga por clave
     * primaria (a diferencia de una query HQL/Criteria normal vía
     * findAll()/findByXxx()) no es algo que se deba dar por sentado sin una
     * prueba explícita que lo confirme para esta versión exacta de Hibernate.
     * Mientras esa prueba no exista, cualquier método que resuelva una
     * entidad por id compara tenant_id (y ahora tambien sucursal_id) contra
     * el contexto actual manualmente. Se lanza el mismo "no encontrado" que
     * un id inexistente (nunca un 403) para no confirmarle a un atacante que
     * el recurso sí existe en otro tenant/sucursal. Ver MenuServiceTest.
     */
    /**
     * Camino usado por pedido.servicio.PedidoService (RF-05): busca el item
     * exactamente igual que obtenerItem() (misma defensa explicita de
     * tenant y sucursal) pero ademas exige que este disponible -- un pedido
     * no puede incluir un plato agotado, ni siquiera si el comensal alcanzo
     * a tenerlo en el carrito antes de que se marcara agotado. Publico a
     * proposito (a diferencia de obtenerItem): es el unico punto de entrada
     * pensado para que otro modulo reuse esta logica, en vez de duplicarla.
     *
     * 2026-09-10: en el camino publico (MenuPublicoController), SucursalContext
     * la puebla explicitamente el controller con la sucursal real de la mesa
     * antes de llegar aca (mismo patron que TenantFilterActivator para
     * TenantContext) -- asi que un intento de pedir un item de OTRA sucursal
     * del mismo tenant cae en el mismo "no encontrado" generico que ya usa
     * el resto de este servicio, en vez de aceptarse silenciosamente.
     */
    @Transactional(readOnly = true)
    public ItemMenu obtenerItemDisponibleParaPedido(Long itemId) {
        ItemMenu item = obtenerItem(itemId);
        if (!item.isDisponible()) {
            throw new MenuExceptions.ItemNoDisponibleException(itemId);
        }
        return item;
    }

    private CategoriaMenu obtenerCategoria(Long id) {
        CategoriaMenu categoria = categoriaMenuRepository.findById(id)
                .filter(CategoriaMenu::isActivo)
                .orElseThrow(() -> new MenuExceptions.CategoriaNoEncontradaException(id));
        if (!perteneceAlTenantActual(categoria.getTenantId()) || !perteneceALaSucursalActual(categoria.getSucursalId())) {
            throw new MenuExceptions.CategoriaNoEncontradaException(id);
        }
        return categoria;
    }

    private ItemMenu obtenerItem(Long id) {
        ItemMenu item = itemMenuRepository.findById(id)
                .filter(ItemMenu::isActivo)
                .orElseThrow(() -> new MenuExceptions.ItemMenuNoEncontradoException(id));
        if (!perteneceAlTenantActual(item.getTenantId()) || !perteneceALaSucursalActual(item.getSucursalId())) {
            throw new MenuExceptions.ItemMenuNoEncontradoException(id);
        }
        return item;
    }

    private boolean perteneceAlTenantActual(Long tenantIdDelRecurso) {
        Long tenantActual = TenantContext.obtenerTenantId();
        return tenantActual != null && tenantActual.equals(tenantIdDelRecurso);
    }

    private boolean perteneceALaSucursalActual(Long sucursalIdDelRecurso) {
        Long sucursalActual = SucursalContext.obtenerSucursalId();
        return sucursalActual != null && sucursalActual.equals(sucursalIdDelRecurso);
    }

    private void validarPrecio(BigDecimal precio) {
        if (precio == null || precio.compareTo(BigDecimal.ZERO) < 0) {
            throw new MenuExceptions.PrecioInvalidoException(precio);
        }
    }

    private Long tenantActual() {
        Long tenantId = TenantContext.obtenerTenantId();
        if (tenantId == null) {
            throw new MenuExceptions.SinTenantEnContextoException();
        }
        return tenantId;
    }

    /**
     * A diferencia de tenantActual(), este metodo NUNCA acepta sucursal
     * ausente, ni siquiera para ADMIN_RESTAURANTE -- desviacion deliberada
     * del patron sucursalActual() de MesaService/PedidoService (donde
     * ADMIN sin sucursal seleccionada significa "ver todas las sucursales",
     * ver el javadoc de SucursalContext). Esa semantica no aplica aca: como
     * cada sucursal tiene una carta totalmente independiente (decision de
     * producto, 2026-09-10), no existe una vista sensata de "la carta de
     * todas las sucursales mezcladas" -- un ADMIN que quiere administrar el
     * menu SIEMPRE debe elegir primero sobre cual sucursal esta trabajando.
     * Reutiliza SucursalExceptions.SucursalNoSeleccionadaException (en vez
     * de una excepcion propia de menu) porque el significado es exactamente
     * el mismo que ya usa ese modulo.
     */
    private Long sucursalActual() {
        Long sucursalId = SucursalContext.obtenerSucursalId();
        if (sucursalId == null) {
            throw new SucursalExceptions.SucursalNoSeleccionadaException();
        }
        return sucursalId;
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
