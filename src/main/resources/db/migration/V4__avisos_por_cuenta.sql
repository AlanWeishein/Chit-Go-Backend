-- Un aviso no debe reaparecer cuando otra cuenta ocupa la misma mesa.
ALTER TABLE aviso
    ADD COLUMN cuenta_id BIGINT NULL,
    ADD COLUMN version INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_aviso_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    ADD INDEX idx_aviso_cuenta_tipo (cuenta_id, tipo, atendido_en);

-- Los avisos anteriores no se pueden asociar con certeza a una cuenta histórica.
-- Se conservan; los nuevos siempre guardan cuenta_id.
