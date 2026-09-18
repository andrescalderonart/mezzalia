-- V21: agrega modelo_glb_url y modelo_usdz_url a items_menu -- primer paso
-- tecnico del catalogo de platos en 3D/AR (valor agregado del producto,
-- discutido 2026-09-11). A diferencia de V20 (sucursal_id, obligatorio para
-- todo item existente), estas columnas son NULLABLE a proposito: la
-- inmensa mayoria de items nunca va a tener un modelo 3D -- se genera por
-- fotogrametria, plato por plato, fuera de esta aplicacion (ver el
-- javadoc de ItemMenu.modeloGlbUrl/modeloUsdzUrl para por que todavia NO
-- hay endpoint de subida, solo el campo de URL).
--
-- Dos columnas, no una: Safari en iOS no soporta WebXR AR (confirmado
-- 2026-09, sin cambios) y solo abre AR Quick Look via un archivo .usdz;
-- Android usa Scene Viewer via .glb. No hay conversion automatica entre
-- ambos formatos con calidad aceptable -- un item con AR real necesita las
-- dos URLs, o ninguna (ver MenuService.actualizarModelo3d, que rechaza
-- cargar una sola).
--
-- Mismo alcance de verificacion honesto que el resto de las migraciones de
-- este proyecto: sintaxis revisada a mano contra MySQL 8, no compilada ni
-- ejecutada en este entorno. El perfil h2 de desarrollo local NO ejecuta
-- esta migracion (ddl-auto genera el esquema directo desde las entidades,
-- Flyway deshabilitado ahi) -- este script solo aplica al perfil mysql.

ALTER TABLE items_menu
    ADD COLUMN modelo_glb_url VARCHAR(500) NULL AFTER imagen_url;

ALTER TABLE items_menu
    ADD COLUMN modelo_usdz_url VARCHAR(500) NULL AFTER modelo_glb_url;
