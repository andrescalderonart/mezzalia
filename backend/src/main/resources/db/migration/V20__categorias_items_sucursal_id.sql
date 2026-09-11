-- V20: agrega sucursal_id a categorias_menu e items_menu (RF-10, ver el
-- aviso de alcance pendiente en Sucursal.java: "Completar
-- CategoriaMenu/ItemMenu/Pedido queda como el siguiente paso explicito,
-- no un olvido").
--
-- A diferencia de V16__mesas_sucursal_id.sql, NO hay backfill: no existe
-- carta real en ningun tenant todavia (confirmado 2026-09-10, proyecto
-- pre-lanzamiento). Si esta migracion falla porque alguna base de datos
-- de desarrollo compartida SI tiene filas en estas tablas, es una señal
-- real de que hay que decidir a mano a que sucursal asignarlas -- no se
-- inventa una asignacion silenciosa (mismo criterio que V16).
--
-- item.sucursal_id SIEMPRE coincide con el de su categoria (ver
-- MenuService.crearItem: se hereda de la categoria, nunca se asigna por
-- separado) -- no hay FK compuesta para exigir esto a nivel de BD, es un
-- invariante de aplicacion, mismo criterio que la unicidad de codigo de
-- mesa (ver MesaService).
--
-- IMPORTANTE -- mismo alcance de verificacion honesto que el resto de las
-- migraciones de este proyecto: sintaxis revisada a mano contra MySQL 8,
-- no compilada ni ejecutada en este entorno -- verificar con
-- `docker-compose up` antes de mergear a produccion. El perfil h2 de
-- desarrollo local NO ejecuta esta migracion (ddl-auto genera el esquema
-- directo desde las entidades, Flyway deshabilitado ahi) -- este script
-- solo aplica al perfil mysql.

ALTER TABLE categorias_menu
    ADD COLUMN sucursal_id BIGINT NOT NULL AFTER tenant_id;

ALTER TABLE categorias_menu
    ADD CONSTRAINT fk_categorias_menu_sucursal
        FOREIGN KEY (sucursal_id) REFERENCES sucursales (id);

CREATE INDEX idx_categorias_menu_sucursal_id ON categorias_menu (sucursal_id);

ALTER TABLE items_menu
    ADD COLUMN sucursal_id BIGINT NOT NULL AFTER tenant_id;

ALTER TABLE items_menu
    ADD CONSTRAINT fk_items_menu_sucursal
        FOREIGN KEY (sucursal_id) REFERENCES sucursales (id);

CREATE INDEX idx_items_menu_sucursal_id ON items_menu (sucursal_id);
