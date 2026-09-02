package com.mezzalia.pedido.controlador;

import com.mezzalia.pedido.servicio.ComensalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Habeas Data (RF-13, documentacion-tecnica.md §10): derecho de supresion
 * del comensal. Restringido a ADMIN_RESTAURANTE (mismo patron que
 * MenuAdminController/MesaAdminController) y NO a MESERO -- el §10 exige
 * ademas "acceso restringido por rol (un mesero no necesita ver el
 * telefono completo, un admin_restaurante si)" para la LECTURA, algo que
 * ComensalDto todavia no aplica (pendiente aparte, no arrancado en este
 * cambio); mientras eso no exista, borrar datos personales de un comensal
 * es al menos tan sensible como leerlos, asi que se restringe con el
 * mismo criterio.
 */
@RestController
@RequestMapping("/api/admin/comensales")
@RequiredArgsConstructor
public class ComensalAdminController {

    private final ComensalService comensalService;

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<Void> eliminarDatos(@PathVariable Long id) {
        comensalService.eliminarDatosComensal(id);
        return ResponseEntity.noContent().build();
    }
}
