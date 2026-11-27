package com.mezzalia.menu.controlador;

import com.mezzalia.menu.dto.ActualizarIngredienteRequest;
import com.mezzalia.menu.dto.CrearIngredienteRequest;
import com.mezzalia.menu.dto.IngredienteDto;
import com.mezzalia.menu.dto.ItemMenuDto;
import com.mezzalia.menu.dto.OrdenIngredientesRequest;
import com.mezzalia.menu.dto.PresentacionRequest;
import com.mezzalia.menu.servicio.IngredienteService;
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
 * Editor de ingredientes de un plato (2026-11-26). Solo ADMIN_RESTAURANTE,
 * igual que el resto de la edicion de la carta (MenuAdminController):
 * cubierto por AutorizacionAdminTest.
 */
@RestController
@RequestMapping("/api/admin/menu")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
public class IngredienteAdminController {

    private final IngredienteService ingredienteService;

    @GetMapping("/items/{itemId}/ingredientes")
    public ResponseEntity<List<IngredienteDto>> listar(@PathVariable Long itemId) {
        return ResponseEntity.ok(ingredienteService.listar(itemId).stream().map(IngredienteDto::desde).toList());
    }

    @PostMapping("/items/{itemId}/ingredientes")
    public ResponseEntity<IngredienteDto> crear(@PathVariable Long itemId, @Valid @RequestBody CrearIngredienteRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(IngredienteDto.desde(ingredienteService.crear(itemId, r.nombre(), r.removible())));
    }

    @PutMapping("/items/{itemId}/ingredientes/orden")
    public ResponseEntity<List<IngredienteDto>> reordenar(@PathVariable Long itemId, @Valid @RequestBody OrdenIngredientesRequest r) {
        return ResponseEntity.ok(ingredienteService.reordenar(itemId, r.ids()).stream().map(IngredienteDto::desde).toList());
    }

    @PatchMapping("/items/{itemId}/presentacion")
    public ResponseEntity<ItemMenuDto> cambiarPresentacion(@PathVariable Long itemId, @Valid @RequestBody PresentacionRequest r) {
        return ResponseEntity.ok(ItemMenuDto.desde(ingredienteService.cambiarPresentacion(itemId, r.presentacion())));
    }

    @PutMapping("/ingredientes/{id}")
    public ResponseEntity<IngredienteDto> actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarIngredienteRequest r) {
        return ResponseEntity.ok(IngredienteDto.desde(ingredienteService.actualizar(id, r.nombre(), r.removible())));
    }

    @DeleteMapping("/ingredientes/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        ingredienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/ingredientes/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngredienteDto> subirImagen(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(IngredienteDto.desde(ingredienteService.actualizarImagen(id, archivo)));
    }
}
