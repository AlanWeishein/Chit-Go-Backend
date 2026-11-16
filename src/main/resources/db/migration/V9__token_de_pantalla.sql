-- Propio de la pantalla pública; nunca reutiliza el token del QR de staff.
-- Se agrega nullable antes de rellenarla: MySQL rechaza evaluar un default no determinista
-- sobre las filas existentes durante ADD COLUMN. El default cubre futuros INSERT por script.
ALTER TABLE local ADD COLUMN token_pantalla CHAR(36) NULL;
UPDATE local SET token_pantalla = LOWER(HEX(RANDOM_BYTES(18)));
ALTER TABLE local MODIFY COLUMN token_pantalla CHAR(36) NOT NULL DEFAULT (LOWER(HEX(RANDOM_BYTES(18)))),
    ADD CONSTRAINT uq_local_token_pantalla UNIQUE (token_pantalla);
