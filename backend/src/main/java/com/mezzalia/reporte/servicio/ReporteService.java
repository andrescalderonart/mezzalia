package com.mezzalia.reporte.servicio;

import com.mezzalia.pedido.PedidoItem;
import com.mezzalia.pedido.PedidoItemRepository;
import com.mezzalia.reporte.dto.IngredienteQuitadoDto;
import com.mezzalia.reporte.dto.PlatoQuitadosDto;
import com.mezzalia.reporte.dto.ReporteQuitadosDto;
import com.mezzalia.sucursal.SucursalContext;
import com.mezzalia.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Que quitan tus clientes" (2026-11-27): el dato que ningun competidor tiene,
 * porque nadie mas captura lo que el comensal personaliza. Sale de
 * pedido_items.sin_ingredientes (la copia de los nombres al momento del
 * pedido), asi que renombrar o borrar un ingrediente no altera el historico.
 *
 * Se agrupa por plato (item_menu_id) y por NOMBRE de ingrediente. Solo
 * pedidos no cancelados. Respeta la sucursal seleccionada del admin, igual
 * que la lista de pedidos.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    static final int MAX_MAS_QUITADOS = 5;

    private final PedidoItemRepository pedidoItemRepository;

    @Transactional(readOnly = true)
    public ReporteQuitadosDto ingredientesQuitados(int dias) {
        Long tenantId = TenantContext.obtenerTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("El reporte requiere un usuario de restaurante");
        }
        LocalDateTime desde = LocalDateTime.now().minusDays(dias);
        Long sucursalId = SucursalContext.obtenerSucursalId();
        List<PedidoItem> lineas = sucursalId != null
                ? pedidoItemRepository.lineasDesdeEnSucursal(tenantId, desde, sucursalId)
                : pedidoItemRepository.lineasDesde(tenantId, desde);

        Map<Long, Acumulado> porPlato = new LinkedHashMap<>();
        for (PedidoItem linea : lineas) {
            Acumulado a = porPlato.computeIfAbsent(linea.getItemMenuId(), id -> new Acumulado());
            a.nombre = linea.getNombreItem(); // el mas reciente gana (los pedidos llegan en orden)
            a.unidades += linea.getCantidad();
            String sin = linea.getSinIngredientes();
            if (sin != null && !sin.isBlank()) {
                a.personalizadas += linea.getCantidad();
                for (String ingrediente : sin.split("\n")) {
                    a.quitados.merge(ingrediente.trim(), linea.getCantidad(), Integer::sum);
                }
            }
        }

        int totalUnidades = 0, totalPersonalizadas = 0;
        List<PlatoQuitadosDto> platos = new ArrayList<>();
        List<IngredienteQuitadoDto> todos = new ArrayList<>();
        for (Map.Entry<Long, Acumulado> e : porPlato.entrySet()) {
            Acumulado a = e.getValue();
            totalUnidades += a.unidades;
            totalPersonalizadas += a.personalizadas;
            if (a.personalizadas == 0) {
                continue; // un plato que nadie personaliza no aporta al reporte
            }
            List<IngredienteQuitadoDto> ingredientes = a.quitados.entrySet().stream()
                    .map(q -> new IngredienteQuitadoDto(q.getKey(), null, q.getValue(), pct(q.getValue(), a.unidades)))
                    .sorted(Comparator.comparingInt(IngredienteQuitadoDto::unidades).reversed())
                    .toList();
            ingredientes.forEach(i -> todos.add(new IngredienteQuitadoDto(i.nombre(), a.nombre, i.unidades(), i.porcentaje())));
            platos.add(new PlatoQuitadosDto(e.getKey(), a.nombre, a.unidades, a.personalizadas,
                    pct(a.personalizadas, a.unidades), ingredientes));
        }
        platos.sort(Comparator.comparingInt(PlatoQuitadosDto::unidadesPersonalizadas).reversed());
        // Ranking global: primero por porcentaje (lo mas revelador para el
        // dueno), y a igual porcentaje por volumen.
        List<IngredienteQuitadoDto> masQuitados = todos.stream()
                .sorted(Comparator.comparingDouble(IngredienteQuitadoDto::porcentaje).reversed()
                        .thenComparing(Comparator.comparingInt(IngredienteQuitadoDto::unidades).reversed()))
                .limit(MAX_MAS_QUITADOS)
                .toList();

        return new ReporteQuitadosDto(dias, totalUnidades, totalPersonalizadas,
                pct(totalPersonalizadas, totalUnidades), masQuitados, platos);
    }

    private static double pct(int parte, int total) {
        return total == 0 ? 0 : Math.round(parte * 1000.0 / total) / 10.0;
    }

    private static final class Acumulado {
        String nombre;
        int unidades;
        int personalizadas;
        final Map<String, Integer> quitados = new LinkedHashMap<>();
    }
}
