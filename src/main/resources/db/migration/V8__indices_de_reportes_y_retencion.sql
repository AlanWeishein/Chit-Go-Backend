CREATE INDEX idx_cuenta_local_cierre ON cuenta(local_id, estado, cerrada_en);
CREATE INDEX idx_pedido_local_listo ON pedido(local_id, listo_en);
CREATE INDEX idx_pedido_local_envio ON pedido(local_id, enviado_en);
CREATE INDEX idx_auditoria_fecha ON registro_auditoria(creado_en, id);
CREATE INDEX idx_auditoria_local_usuario ON registro_auditoria(local_id, usuario_id, creado_en);
