-- V18: control de turnos de trabajo (marcar entrada/marcar salida), pedido
-- explicito de Andres el 2026-09-07 para poder ver cuanto tiempo trabajado
-- por dia lleva cada usuario. Decision de negocio confirmada con el:
--
-- 1) El unico dato confiable de "estoy trabajando" es que la persona
--    marque entrada y salida a mano -- login/refresh de JWT NO sirve como
--    proxy (una pestana abierta se sigue refrescando sola aunque el
--    usuario ya se haya ido, ver client.js/intentarReseleccionAutomatica),
--    y usuario_sucursales (la asignacion mesero<->sucursal) es
--    explicitamente una tabla sin historial (ver AsignacionSucursal.java),
--    asi que no hay ningun dato ya existente del que se pueda derivar esto.
-- 2) Un turno abierto que nadie cierra nunca (se olvidaron de marcar
--    salida) se cierra SOLO despues de TOPE_HORAS_TURNO horas seguidas
--    (12, ver TurnoService) -- a proposito NO es un corte a medianoche:
--    un restaurante que cierra pasada la medianoche tiene turnos reales
--    que cruzan de un dia calendario a otro, y cortar por reloj los
--    partiria a la mitad. Este cierre automatico ademas es LAZY (se aplica
--    la primera vez que algo lee/toca ese turno, ver
--    TurnoService.cerrarSiVencido) -- este proyecto no tiene ningun
--    scheduler/cron corriendo (ver el resto del backend), asi que agregar
--    uno solo para esto no se justificaba.
-- 3) Para reportar "horas por dia", cada turno se le atribuye ENTERO al
--    dia calendario de su `inicio` -- nunca se parte entre dos dias aunque
--    cruce medianoche (ver TurnoService.horasPorDiaDe).
--
-- sucursal_id es NULLABLE a proposito: un ADMIN_RESTAURANTE puede marcar
-- turno sin tener ninguna sucursal seleccionada (para ese rol elegir
-- sucursal siempre fue opcional, ver AuthService.misSucursalesDisponibles)
-- -- un MESERO en cambio siempre va a tener una porque ya esta obligado a
-- elegirla antes de poder hacer nada mas (ver SucursalContext), pero no se
-- fuerza NOT NULL a nivel de base de datos para no acoplar esta tabla a
-- esa regla de otro modulo, que podria cambiar.
--
-- IMPORTANTE -- mismo alcance de verificacion honesto que V15/V16/V17:
-- esta sintaxis fue revisada a mano contra MySQL 8, nunca compilada ni
-- ejecutada en este entorno (sin Maven/JDK disponibles aca). Los tests de
-- la suite usan H2 con ddl-auto:create-drop generado desde las entidades
-- (Flyway deshabilitado en el perfil de test), asi que este archivo NUNCA
-- corre en CI -- verificar con `docker-compose up` contra MySQL real antes
-- de considerar esto probado en produccion.

CREATE TABLE turnos (
    id                 BIGINT   NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT   NOT NULL,
    usuario_id         BIGINT   NOT NULL,
    sucursal_id        BIGINT   NULL,
    inicio             DATETIME NOT NULL,
    fin                DATETIME NULL,
    cierre_automatico  TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_turnos_usuario_fin (usuario_id, fin),
    KEY idx_turnos_tenant_id (tenant_id),
    KEY idx_turnos_usuario_inicio (usuario_id, inicio),
    CONSTRAINT fk_turnos_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_turnos_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios_internos (id),
    CONSTRAINT fk_turnos_sucursal
        FOREIGN KEY (sucursal_id) REFERENCES sucursales (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
