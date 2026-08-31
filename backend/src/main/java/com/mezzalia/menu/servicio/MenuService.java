package com.mezzalia.menu.servicio;

import com.mezzalia.menu.CategoriaMenu;
import com.mezzalia.menu.CategoriaMenuRepository;
import com.mezzalia.menu.HistorialPrecioItem;
import com.mezzalia.menu.HistorialPrecioItemRepository;
import com.mezzalia.menu.ItemMenu;
import com.mezzalia.menu.ItemMenuRepository;
import com.mezzalia.menu.excepcion.MenuExceptions;
import com.mezzalia.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final CategoriaMenuRepository categoriaMenuRepository;
    private final ItemMenuRepository itemMenuRepository;
    private final HistorialPrecioItemRepository historialPrecioItemRepository;
    private final AlmacenamientoImagenService almacenamientoImagenService;

    @Transactional(readOnly = true)
    public List<CategoriaMenu> listarCategorias() {
        return categoriaMenuRepository.findByActivoTrueOrderByOrdenAsc();
    }

    @Transactional
    public CategoriaMenu crearCategoria(String nombre, int orden) {
        CategoriaMenu categoria = new CategoriaMenu();
        categoria.setTenantId(tenantActual());
        categoria.setNombre(nombre);
        categoria.setOrden(orden);
        return categoriaMenuRepository.save(categoria);
    }

    @Transactional(readOnly = true)
    public List<ItemMenu> listarItemsDeCategoria(Long categoriaId) {
        obtenerCategoria(categoriaId);
        return itemMenuRepository.findByCategoriaIdAndActivoTrue(categoriaId);
    }

    @Transactional
    public ItemMenu crearItem(Long categoriaId, String nombre, String descripcion, BigDecimal precio, String imagenUrl) {
        validarPrecio(precio);
        obtenerCategoria(categoriaId);

        ItemMenu item = new ItemMenu();
        item.setTenantId(tenantActual());
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
     * entidad por id compara tenant_id contra TenantContext manualmente.
     * Se lanza el mismo "no encontrado" que un id inexistente (nunca un 403)
     * para no confirmarle a un atacante que el recurso sí existe en otro
     * tenant. Ver MenuServiceTest.
     */
    private CategoriaMenu obtenerCategoria(Long id) {
        CategoriaMenu categoria = categoriaMenuRepository.findById(id)
                .filter(CategoriaMenu::isActivo)
                .orElseThrow(() -> new MenuExceptions.CategoriaNoEncontradaException(id));
        if (!perteneceAlTenantActual(categoria.getTenantId())) {
            throw new MenuExceptions.CategoriaNoEncontradaException(id);
        }
        return categoria;
    }

    private ItemMenu obtenerItem(Long id) {
        ItemMenu item = itemMenuRepository.findById(id)
                .filter(ItemMenu::isActivo)
                .orElseThrow(() -> new MenuExceptions.ItemMenuNoEncontradoException(id));
        if (!perteneceAlTenantActual(item.getTenantId())) {
            throw new MenuExceptions.ItemMenuNoEncontradoException(id);
        }
        return item;
    }

    private boolean perteneceAlTenantActual(Long tenantIdDelRecurso) {
        Long tenantActual = TenantContext.obtenerTenantId();
        return tenantActual != null && tenantActual.equals(tenantIdDelRecurso);
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

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
