package com.mezzalia.menu.controlador;

import com.mezzalia.menu.CategoriaMenu;
import com.mezzalia.menu.ItemMenu;
import com.mezzalia.menu.dto.ActualizarItemRequest;
import com.mezzalia.menu.dto.CategoriaMenuDto;
import com.mezzalia.menu.dto.CrearCategoriaRequest;
import com.mezzalia.menu.dto.CrearItemRequest;
import com.mezzalia.menu.dto.DisponibilidadRequest;
import com.mezzalia.menu.dto.ItemMenuDto;
import com.mezzalia.menu.servicio.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RF-09: cualquier usuario autenticado del tenant (ADMIN_RESTAURANTE o
 * MESERO) puede VER la carta (la necesita para tomar pedidos) -- por eso los
 * GET no llevan @PreAuthorize adicional, solo la autenticación que ya exige
 * SecurityConfig para /api/admin/**. Crear/editar/borrar SÍ está restringido
 * a ADMIN_RESTAURANTE: un mesero no debe poder tocar precios ni la carta.
 *
 * 2026-09-10: cada sucursal tiene su propia carta -- todos los endpoints de
 * este controller operan sobre la sucursal actualmente seleccionada por el
 * usuario autenticado (ver MenuService.sucursalActual(), que exige
 * selección siempre, a diferencia de Mesa/Pedido). El frontend debe forzar
 * la selección de sucursal antes de dejar entrar a la sección de carta.
 */
@RestController
@RequestMapping("/api/admin/menu")
@RequiredArgsConstructor
public class MenuAdminController {

    private final MenuService menuService;

    @GetMapping("/categorias")
    public ResponseEntity<List<CategoriaMenuDto>> listarCategorias() {
        List<CategoriaMenuDto> categorias = menuService.listarCategorias().stream()
                .map(CategoriaMenuDto::desde)
                .toList();
        return ResponseEntity.ok(categorias);
    }

    @PostMapping("/categorias")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<CategoriaMenuDto> crearCategoria(@Valid @RequestBody CrearCategoriaRequest request) {
        CategoriaMenu categoria = menuService.crearCategoria(request.nombre(), request.orden());
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoriaMenuDto.desde(categoria));
    }

    @GetMapping("/categorias/{categoriaId}/items")
    public ResponseEntity<List<ItemMenuDto>> listarItems(@PathVariable Long categoriaId) {
        List<ItemMenuDto> items = menuService.listarItemsDeCategoria(categoriaId).stream()
                .map(ItemMenuDto::desde)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping("/items")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<ItemMenuDto> crearItem(@Valid @RequestBody CrearItemRequest request) {
        ItemMenu item = menuService.crearItem(
                request.categoriaId(), request.nombre(), request.descripcion(),
                request.precio(), request.imagenUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemMenuDto.desde(item));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<ItemMenuDto> actualizarItem(@PathVariable Long id, @Valid @RequestBody ActualizarItemRequest request) {
        ItemMenu item = menuService.actualizarItem(
                id, request.nombre(), request.descripcion(), request.precio(), request.imagenUrl());
        return ResponseEntity.ok(ItemMenuDto.desde(item));
    }

    @PatchMapping("/items/{id}/disponibilidad")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<ItemMenuDto> cambiarDisponibilidad(@PathVariable Long id, @Valid @RequestBody DisponibilidadRequest request) {
        ItemMenu item = menuService.cambiarDisponibilidad(id, request.disponible());
        return ResponseEntity.ok(ItemMenuDto.desde(item));
    }

    @PostMapping(value = "/items/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<ItemMenuDto> subirImagen(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        ItemMenu item = menuService.actualizarImagen(id, archivo);
        return ResponseEntity.ok(ItemMenuDto.desde(item));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<Void> eliminarItem(@PathVariable Long id) {
        menuService.eliminarItem(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 2026-09-10: copia la carta de otra sucursal (sucursalOrigenId) hacia
     * la sucursal que el usuario tiene seleccionada ahora mismo. Rechaza si
     * la sucursal destino ya tiene carta activa (ver
     * MenuExceptions.SucursalDestinoYaTieneCartaException) -- nunca mezcla
     * ni sobrescribe en silencio.
     */
    @PostMapping("/duplicar")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<List<CategoriaMenuDto>> duplicarMenu(@RequestParam Long sucursalOrigenId) {
        List<CategoriaMenuDto> categorias = menuService.duplicarMenuDesdeSucursal(sucursalOrigenId).stream()
                .map(CategoriaMenuDto::desde)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(categorias);
    }
}
