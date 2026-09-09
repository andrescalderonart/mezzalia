-- V19: registro auditable de consentimiento para RF-13/Habeas Data (ver
-- documentacion-tecnica.md §7 y §10). Hasta ahora `comensales` guardaba UN
-- SOLO estado implicito: "si la fila existe, es porque hubo consentimiento"
-- (ver PedidoService.crearComensalSiAplica) -- pero no quedaba ningun
-- registro explicito de CUANDO se dio ese consentimiento, y Ley 1581/2012
-- exige poder auditarlo, no solo inferirlo de que el dato este ahi. Estos
-- dos campos no cambian NINGUN comportamiento de la aplicacion hoy -- solo
-- hacen explicito, en su propia columna, lo que antes solo se podia inferir.
--
-- Backfill de filas existentes: TODA fila que ya existe en esta tabla fue
-- creada unicamente porque `request.consentimiento()` era true en ese
-- momento (es el unico camino de creacion, ver crearComensalSiAplica) --
-- asi que consentimiento_marketing=1 para filas existentes no es una
-- suposicion, es el mismo invariante que ya garantiza el codigo actual.
-- consentimiento_registrado_en se rellena con `creado_en` porque en el
-- codigo actual ambos eventos ocurren en el mismo instante (no hay ningun
-- caso hoy donde el comensal se cree primero y consienta despues).
--
-- IMPORTANTE -- mismo alcance de verificacion honesto que V15/V16/V17/V18:
-- sintaxis revisada a mano contra MySQL 8, nunca ejecutada en este entorno
-- (sin Maven/Docker disponibles aca). Este archivo nunca corre en CI (los
-- tests usan H2 con ddl-auto desde las entidades, Flyway deshabilitado en
-- el perfil de test) -- verificar contra MySQL real (`docker-compose up`)
-- antes de considerar esto probado en produccion.

ALTER TABLE comensales
    ADD COLUMN consentimiento_marketing TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN consentimiento_registrado_en DATETIME NULL;

UPDATE comensales
SET consentimiento_marketing = 1,
    consentimiento_registrado_en = creado_en
WHERE consentimiento_marketing = 0;
