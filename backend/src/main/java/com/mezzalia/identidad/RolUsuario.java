package com.mezzalia.identidad;

/**
 * RF-09: admin_restaurante puede editar la carta; mesero solo ve y actualiza
 * estado de pedidos. SUPER_ADMIN es el equipo de Mezzalia (§10: separado del
 * resto de roles, sin tenant asociado).
 */
public enum RolUsuario {
    SUPER_ADMIN,
    ADMIN_RESTAURANTE,
    MESERO
}
