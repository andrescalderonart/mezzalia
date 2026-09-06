-- V17: RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §5/§8) -- asignacion
-- de sucursales a usuarios internos. Decision de negocio ya resuelta
-- explicitamente con el equipo (2026-09): un MESERO puede estar asignado a
-- VARIAS sucursales del mismo tenant (muchos-a-muchos), la asignacion la
-- gestiona EXCLUSIVAMENTE ADMIN_RESTAURANTE, y recien al iniciar sesion el
-- MESERO elige en cual de SUS sucursales asignadas va a trabajar (ver
-- JwtService/AuthService/SesionController: el JWT de acceso lleva un claim
-- opcional "sucursalId" que se agrega en un SEGUNDO paso explicito, nunca
-- en el login inicial). La carta (categorias_menu/items_menu) NO varia por
-- sucursal -- decision de negocio explicita, distinta a la de arriba: sigue
-- siendo una sola carta compartida por tenant, esta migracion no la toca.
--
-- tenant_id se guarda en esta tabla ADEMAS de derivarse de usuario_id/
-- sucursal_id -- mismo criterio explicito que el resto del proyecto (ver
-- EntidadConTenant, SucursalRepository/AsignacionSucursalRepository):
-- permite filtrar esta tabla por tenant sin JOIN, y el FK
-- fk_usuario_sucursales_tenant es un chequeo redundante de base de datos
-- ante el caso -- que nunca deberia pasar, pero la unica forma real de
-- estar seguro es que la base de datos tambien lo imponga -- de que
-- usuario y sucursal terminen perteneciendo a tenants distintos por un bug
-- de aplicacion (ver AsignacionSucursalService.asignar: valida ambos
-- explicitamente contra el tenant actual ANTES de este INSERT, este FK es
-- el backstop, no el primer control).
--
-- Backfill: cada UsuarioInterno con rol MESERO que exista HOY se asigna a
-- la sucursal ACTIVA de menor id de su propio tenant (equivalente a "la
-- sucursal mas antigua todavia activa"). A PROPOSITO no se hace matcheando
-- por nombre = 'Sucursal Principal' (que es como V16__mesas_sucursal_id.sql
-- resolvio el mismo problema para Mesa): en V16 esa suposicion era segura
-- porque en ese momento NINGUN tenant podia tener mas de una sucursal (el
-- concepto recien se estaba creando en la migracion anterior, V15). Para
-- V17 esa garantia ya NO existe -- V15/V16 llevan tiempo en produccion, el
-- CRUD de sucursales (SucursalAdminController) ya esta expuesto, y
-- `sucursales.nombre` NUNCA tuvo una restriccion UNIQUE (ver Sucursal.java)
-- -- nada impide hoy que un ADMIN_RESTAURANTE haya creado una segunda
-- sucursal llamada tambien "Sucursal Principal", o que haya desactivado la
-- original. Matchear por nombre en ese escenario asignaria a un mesero a
-- la sucursal EQUIVOCADA (o a ninguna, si la original ya esta inactiva) sin
-- ningun error visible. MIN(id) entre las activas de ese tenant es
-- determinista y no depende de un string que el propio negocio puede
-- editar libremente.
--
-- Un MESERO cuyo tenant no tiene NINGUNA sucursal activa (ya sea porque
-- nunca se creo ninguna -- ver la nota abajo sobre TenantService.crearTenant
-- -- o, en teoria, por datos corruptos) simplemente no recibe ninguna fila
-- aca: no hay ninguna sucursal valida a la que asignarlo todavia, y
-- fabricar una placeholder inventada seria peor que dejarlo sin asignar
-- (el ADMIN_RESTAURANTE de ese tenant de todos modos ya no podia crear
-- Mesas para ese mesero sin una sucursal real, ver MesaService.crearMesa
-- -- esta migracion no empeora nada que no estuviera ya roto para ese caso
-- limite).
--
-- NOTA aparte, no resuelta por esta migracion: TenantService.crearTenant no
-- crea automaticamente una sucursal default para un tenant NUEVO (a
-- diferencia del backfill de V15, que solo corrio una vez para los tenants
-- que YA existian en ese momento) -- cualquier tenant dado de alta despues
-- de V15 arranca con cero sucursales hasta que su ADMIN_RESTAURANTE cree
-- una a mano. Es un gap real y anterior a esta rama (afecta a
-- MesaService.crearMesa igual que a esta migracion), documentado aca para
-- que no se lea como un olvido de V17, pero fuera de alcance arreglarlo en
-- esta migracion.
--
-- IMPORTANTE -- mismo alcance de verificacion honesto que V15/V16/V17: esta
-- sintaxis fue revisada a mano contra MySQL 8, nunca compilada ni ejecutada
-- en este entorno (sin Maven/JDK disponibles aca). Los tests de la suite
-- usan H2 con ddl-auto:create-drop generado desde las entidades (Flyway
-- deshabilitado en el perfil de test), asi que este archivo NUNCA corre en
-- CI -- verificar con `docker-compose up` contra MySQL real antes de
-- considerar esto probado en produccion.

CREATE TABLE usuario_sucursales (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT   NOT NULL,
    usuario_id  BIGINT   NOT NULL,
    sucursal_id BIGINT   NOT NULL,
    creado_en   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_usuario_sucursales_usuario_sucursal (usuario_id, sucursal_id),
    KEY idx_usuario_sucursales_tenant_id (tenant_id),
    KEY idx_usuario_sucursales_sucursal_id (sucursal_id),
    CONSTRAINT fk_usuario_sucursales_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_usuario_sucursales_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios_internos (id),
    CONSTRAINT fk_usuario_sucursales_sucursal
        FOREIGN KEY (sucursal_id) REFERENCES sucursales (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO usuario_sucursales (tenant_id, usuario_id, sucursal_id)
SELECT ui.tenant_id, ui.id, principal.sucursal_id
FROM usuarios_internos ui
JOIN (
    SELECT tenant_id, MIN(id) AS sucursal_id
    FROM sucursales
    WHERE activa = 1
    GROUP BY tenant_id
) principal ON principal.tenant_id = ui.tenant_id
WHERE ui.rol = 'MESERO';
