package com.mezzalia.menu.controlador;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/publico/mesas")
@RequiredArgsConstructor
public class MenuPublicoController {

    // TODO (módulo menu): inyectar MenuService y SesionMesaService

    /** RF-01: el comensal accede a la carta sin login, vía token único de mesa. */
    @GetMapping("/{token}/carta")
    public ResponseEntity<Void> obtenerCarta(@PathVariable String token) {
        // TODO: validar sesión de mesa por token, resolver sucursal y tenant,
        //       devolver categorías + ítems disponibles de la carta.
        return ResponseEntity.ok().build();
    }

    /** RF-03: el comensal envía su carrito y se crea el pedido. */
    @PostMapping("/{token}/pedidos")
    public ResponseEntity<Void> crearPedido(@PathVariable String token, @RequestBody Object body) {
        // TODO: validar clave_idempotencia (RF-04), recalcular precios en servidor (RF-05),
        //       persistir pedido y emitir evento WebSocket al panel del restaurante (RF-06).
        return ResponseEntity.ok().build();
    }
}