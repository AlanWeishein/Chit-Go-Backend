CREATE TABLE suscripcion_push (
    id BIGINT NOT NULL AUTO_INCREMENT,
    local_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    endpoint_hash VARCHAR(64) NOT NULL,
    p256dh VARCHAR(128) NOT NULL,
    auth VARCHAR(32) NOT NULL,
    actualizada_en TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_push_endpoint UNIQUE (endpoint_hash),
    INDEX idx_push_usuario (usuario_id, local_id),
    CONSTRAINT fk_push_local FOREIGN KEY (local_id) REFERENCES local (id),
    CONSTRAINT fk_push_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
