-- V14: cierra la carrera real de MesaService.crearMesa (RF-09) con un
-- backstop de base de datos. Ver el comentario largo en Mesa.java sobre por
-- que esto es una columna espejo mantenida por la aplicacion
-- (`codigo_unico`) y no un indice funcional de MySQL 8: asi el mismo
-- UNIQUE KEY se puede generar tambien desde la entidad JPA para el perfil
-- h2 de desarrollo/tests (ddl-auto desde las entidades) -- mismo patron que
-- V11__pedidos.sql con clave_idempotencia.
--
-- IMPORTANTE -- alcance real de esta verificacion: ninguna suite de tests
-- de este proyecto corre esta migracion (los tests usan H2 con
-- ddl-auto:create-drop desde las entidades, Flyway deshabilitado -- ver
-- backend/src/test/resources/application.yml) ni existe un job de CI que
-- levante MySQL real. Esta sintaxis se reviso a mano contra la
-- documentacion de MySQL 8, no se ejecuto ni se compilo en este entorno.
-- Verificar de verdad con `docker-compose up` (o el CI del dia que agregue
-- un servicio de MySQL) antes de dar esto por cerrado en produccion.
--
-- Backfill ANTES de agregar el UNIQUE KEY, a proposito: si hoy ya existiera
-- una violacion real de este invariante entre datos existentes (dos mesas
-- activas con el mismo codigo para el mismo tenant), el ALTER de abajo
-- tiene que fallar fuerte al correr la migracion -- no quedar enmascarado
-- silenciosamente.

ALTER TABLE mesas
    ADD COLUMN codigo_unico VARCHAR(50) NULL AFTER codigo;

UPDATE mesas SET codigo_unico = codigo WHERE activa = 1;

ALTER TABLE mesas
    ADD CONSTRAINT uq_mesas_tenant_codigo_unico UNIQUE (tenant_id, codigo_unico);
