-- Baja lógica: conserva las cuentas, tickets y asignaciones históricas.
ALTER TABLE mesa
    ADD COLUMN activa BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN motivo_inhabilitacion VARCHAR(255) NULL,
    ADD COLUMN version INT NOT NULL DEFAULT 0;
CREATE INDEX idx_mesa_local_activa ON mesa(local_id, activa, numero);
