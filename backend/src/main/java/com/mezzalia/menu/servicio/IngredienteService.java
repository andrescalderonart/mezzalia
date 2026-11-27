package com.mezzalia.menu.servicio;

import com.mezzalia.menu.IngredienteItem;
import com.mezzalia.menu.IngredienteItemRepository;
import com.mezzalia.menu.ItemMenu;
import com.mezzalia.menu.ItemMenuRepository;
import com.mezzalia.menu.PresentacionIngredientes;
import com.mezzalia.menu.excepcion.MenuExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ingredientes de un plato para la vista "desarmada" y el "sin cebolla"
 * (2026-11-26). Todo pasa por MenuService.obtenerItemDelContexto, que exige
 * que el plato sea del tenant y la sucursal actuales: un admin solo edita
 * los ingredientes de su propia carta.
 */
@Service
@RequiredArgsConstructor
public class IngredienteService {

    private final IngredienteItemRepository ingredienteRepository;
    private final ItemMenuRepository itemMenuRepository;
    private final MenuService menuService;
    private final AlmacenamientoImagenService almacenamientoImagenService;

    @Transactional(readOnly = true)
    public List<IngredienteItem> listar(Long itemId) {
        ItemMenu item = menuService.obtenerItemDelContexto(itemId);
        return ingredienteRepository.findByItemMenuIdOrderByOrdenAscIdAsc(item.getId());
    }

    /** Se agrega al final (abajo). */
    @Transactional
    public IngredienteItem crear(Long itemId, String nombre, Boolean removible) {
        ItemMenu item = menuService.obtenerItemDelContexto(itemId);
        List<IngredienteItem> actuales = ingredienteRepository.findByItemMenuIdOrderByOrdenAscIdAsc(item.getId());
        IngredienteItem ing = new IngredienteItem();
        ing.setTenantId(item.getTenantId());
        ing.setItemMenuId(item.getId());
        ing.setNombre(nombre.trim());
        ing.setRemovible(removible == null || removible);
        ing.setOrden(actuales.isEmpty() ? 0 : actuales.get(actuales.size() - 1).getOrden() + 1);
        return ingredienteRepository.save(ing);
    }

    @Transactional
    public IngredienteItem actualizar(Long ingredienteId, String nombre, boolean removible) {
        IngredienteItem ing = obtener(ingredienteId);
        ing.setNombre(nombre.trim());
        ing.setRemovible(removible);
        return ingredienteRepository.save(ing);
    }

    @Transactional
    public void eliminar(Long ingredienteId) {
        ingredienteRepository.delete(obtener(ingredienteId));
    }

    @Transactional
    public IngredienteItem actualizarImagen(Long ingredienteId, MultipartFile archivo) {
        IngredienteItem ing = obtener(ingredienteId);
        ing.setImagenUrl(almacenamientoImagenService.guardar(archivo));
        return ingredienteRepository.save(ing);
    }

    /**
     * Reordena con la lista completa de ids del plato. Tiene que ser
     * exactamente el mismo conjunto: un id de mas o de menos (de otro plato,
     * repetido u olvidado) se rechaza como "no encontrado".
     */
    @Transactional
    public List<IngredienteItem> reordenar(Long itemId, List<Long> idsEnOrden) {
        ItemMenu item = menuService.obtenerItemDelContexto(itemId);
        List<IngredienteItem> actuales = ingredienteRepository.findByItemMenuIdOrderByOrdenAscIdAsc(item.getId());
        Map<Long, IngredienteItem> porId = actuales.stream().collect(Collectors.toMap(IngredienteItem::getId, Function.identity()));
        if (idsEnOrden.size() != actuales.size() || !porId.keySet().equals(new HashSet<>(idsEnOrden))) {
            Long ajeno = idsEnOrden.stream().filter(id -> !porId.containsKey(id)).findFirst().orElse(null);
            throw new MenuExceptions.IngredienteNoEncontradoException(ajeno);
        }
        for (int i = 0; i < idsEnOrden.size(); i++) {
            porId.get(idsEnOrden.get(i)).setOrden(i);
        }
        ingredienteRepository.saveAll(actuales);
        return ingredienteRepository.findByItemMenuIdOrderByOrdenAscIdAsc(item.getId());
    }

    @Transactional
    public ItemMenu cambiarPresentacion(Long itemId, PresentacionIngredientes presentacion) {
        ItemMenu item = menuService.obtenerItemDelContexto(itemId);
        item.setPresentacionIngredientes(presentacion);
        return itemMenuRepository.save(item);
    }

    /** Ingrediente cuyo plato es del tenant y la sucursal actuales; si no, "no encontrado". */
    private IngredienteItem obtener(Long ingredienteId) {
        IngredienteItem ing = ingredienteRepository.findById(ingredienteId)
                .orElseThrow(() -> new MenuExceptions.IngredienteNoEncontradoException(ingredienteId));
        try {
            menuService.obtenerItemDelContexto(ing.getItemMenuId());
        } catch (MenuExceptions.ItemMenuNoEncontradoException e) {
            throw new MenuExceptions.IngredienteNoEncontradoException(ingredienteId);
        }
        return ing;
    }
}
