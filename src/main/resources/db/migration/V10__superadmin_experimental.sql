-- Experimento aprobado: solo alta de local y desbloqueo de Gerente, sin permisos operativos.
-- No crea usuarios ni incluye credenciales. Aprovisionamiento mediante el perfil script.
INSERT INTO rol (codigo, nombre) VALUES ('SUPERADMIN', 'SuperAdmin');

INSERT INTO permiso (codigo, descripcion) VALUES
    ('local.crear', 'Crear un local con su Gerente/Dueño'),
    ('gerente.desbloquear', 'Desbloquear al Gerente/Dueño de un local');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r CROSS JOIN permiso p
WHERE r.codigo = 'SUPERADMIN' AND p.codigo IN ('local.crear', 'gerente.desbloquear');
