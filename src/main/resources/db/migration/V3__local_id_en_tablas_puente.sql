-- =====================================================================
-- Chit&Go — V3: local_id en item_pedido, mozo_mesa y plato_etiqueta
-- Va en chit-go-back/src/main/resources/db/migration/ como
-- V3__local_id_en_tablas_puente.sql, en el mismo PR que la V2.
--
-- Decidido el 15/10: las tres llevan local_id común, sin FK compuesta,
-- para cumplir RNF-10 en todas las tablas. La V1 ya estaba mergeada y
-- una migración mergeada no se edita, así que va en una nueva. Que no se
-- mezclen locales lo controla el Service, no la base.
--
-- Se agrega como NULL, se rellena desde la tabla padre y después pasa a
-- NOT NULL con su FK. Si las tablas están vacías el UPDATE no toca nada.
-- La FK crea sola el índice de local_id.
-- =====================================================================

-- item_pedido: el local sale del pedido
ALTER TABLE item_pedido ADD COLUMN local_id BIGINT NULL;
UPDATE item_pedido ip
    JOIN pedido p ON p.id = ip.pedido_id
SET ip.local_id = p.local_id;
ALTER TABLE item_pedido
    MODIFY local_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_item_pedido_local FOREIGN KEY (local_id) REFERENCES local (id);

-- mozo_mesa: el local sale de la mesa
ALTER TABLE mozo_mesa ADD COLUMN local_id BIGINT NULL;
UPDATE mozo_mesa mm
    JOIN mesa m ON m.id = mm.mesa_id
SET mm.local_id = m.local_id;
ALTER TABLE mozo_mesa
    MODIFY local_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_mozo_mesa_local FOREIGN KEY (local_id) REFERENCES local (id);

-- plato_etiqueta: el local sale del plato
ALTER TABLE plato_etiqueta ADD COLUMN local_id BIGINT NULL;
UPDATE plato_etiqueta pe
    JOIN plato p ON p.id = pe.plato_id
SET pe.local_id = p.local_id;
ALTER TABLE plato_etiqueta
    MODIFY local_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_plato_etiqueta_local FOREIGN KEY (local_id) REFERENCES local (id);
