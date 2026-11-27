-- V24: "pedido de la mesa" (2026-11-27). Varias personas piden desde su
-- celular con el mismo QR; el mesero confirma antes de que entre a cocina.
--
-- 1) pedidos.apodo: nombre o apodo OPCIONAL de quien pidio ("Andres"), para
--    que el mesero lleve cada plato a la persona correcta. No es un dato de
--    contacto (eso vive en comensales, con consentimiento); igual se guarda
--    solo lo que la persona escribe, y puede ir vacio.
-- 2) El estado POR_CONFIRMAR no necesita cambio de esquema: pedidos.estado
--    es VARCHAR(20) sin CHECK (ver V11), y "POR_CONFIRMAR" cabe.
--
-- Mismo alcance de verificacion honesto que V15-V23: revisado a mano contra
-- MySQL 8, no ejecutado aqui (los tests usan H2 desde las entidades).

ALTER TABLE pedidos
    ADD COLUMN apodo VARCHAR(40) NULL AFTER notas;
