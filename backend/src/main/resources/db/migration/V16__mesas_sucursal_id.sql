-- V16: agrega sucursal_id a mesas (RF-10, ver V15__sucursales.sql y el
-- aviso de alcance en Sucursal.java). Todas las mesas existentes se asignan
-- a la "Sucursal Principal" de su propio tenant (creada en V15) -- ningun
-- tenant existente tenia mas de una sucursal antes de esta migracion (el
-- concepto no existia), asi que esta asignacion es exacta, no una
-- aproximacion.
--
-- Mismo patron que V14__mesas_unicidad_codigo_activo.sql: backfill ANTES de
-- agregar NOT NULL/FK, para que un fallo de integridad real (no deberia
-- haber ninguno) se note en la migracion, no quede enmascarado.
--
-- IMPORTANTE -- mismo alcance de verificacion honesto que el resto de las
-- migraciones de este proyecto: ninguna suite de tests corre esto (los
-- tests usan H2 con ddl-auto:create-drop desde las entidades, Flyway
-- deshabilitado) ni hay CI con MySQL real. Sintaxis revisada a mano contra
-- MySQL 8, no compilada ni ejecutada en este entorno -- verificar con
-- `docker-compose up` antes de mergear a produccion.

ALTER TABLE mesas
    ADD COLUMN sucursal_id BIGINT NULL AFTER tenant_id;

UPDATE mesas m
    JOIN sucursales s ON s.tenant_id = m.tenant_id AND s.nombre = 'Sucursal Principal'
    SET m.sucursal_id = s.id;

ALTER TABLE mesas
    MODIFY COLUMN sucursal_id BIGINT NOT NULL;

ALTER TABLE mesas
    ADD CONSTRAINT fk_mesas_sucursal
        FOREIGN KEY (sucursal_id) REFERENCES sucursales (id);

CREATE INDEX idx_mesas_sucursal_id ON mesas (sucursal_id);
