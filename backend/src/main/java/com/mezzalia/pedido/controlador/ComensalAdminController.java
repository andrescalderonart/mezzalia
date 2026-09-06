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
 * MenuAdminController/MesaAdminController) y NO a MESERO -- borrar datos
 * personales de un comensal es al menos tan sensible como leerlos, asi
 * que se restringe con el mismo criterio que la lectura. La otra mitad de
 * §10 ("un mesero no necesita ver el telefono completo, un
 * admin_restaurante si") ya esta aplicada del lado de lectura en
 * ComensalDto/PedidoAdminController -- las dos rutas quedan consistentes:
 * MESERO ni ve el telefono completo ni puede borrarlo.
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
