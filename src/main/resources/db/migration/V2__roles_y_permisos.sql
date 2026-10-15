-- =====================================================================
-- Chit&Go — V2: roles y permisos (datos fijos)
-- Va en chit-go-back/src/main/resources/db/migration/ como
-- V2__roles_y_permisos.sql. Son datos fijos, iguales para todos los
-- locales y también van en producción (los datos de prueba van aparte,
-- en db/dev/).
--
-- Sale de la matriz "Permisos por rol" del DER v3 de Figma, validada por
-- el grupo el 06/10 y cruzada con chit-go-endpoints-api el 15/10.
-- cuenta.unir_mesas (RF-37) pasó al MVP 2 el 10/10 y no se carga.
-- Se inserta por codigo, no por id, así no depende del AUTO_INCREMENT.
--
-- Cuando se cargue, tiene que dar: 5 roles, 16 permisos, 20 filas en
-- rol_permiso (GERENTE_DUENO 3, ADMIN 8, MOZO 3, CAJA 5, COCINA 1).
-- =====================================================================


-- ---------------------------------------------------------------------
-- Roles
-- ---------------------------------------------------------------------

INSERT INTO rol (codigo, nombre) VALUES
    ('GERENTE_DUENO', 'Gerente/Dueño'),
    ('ADMIN',         'Admin'),
    ('MOZO',          'Mozo'),
    ('CAJA',          'Caja'),
    ('COCINA',        'Cocina');


-- ---------------------------------------------------------------------
-- Permisos
-- Cada permiso es una acción que el servidor controla en el endpoint.
-- Los que tapan una acción también tapan la lectura de lo mismo: por
-- ejemplo pedido.aprobar_rechazar cubre GET /caja/pedidos y
-- pedido.cambiar_estado_cocina cubre GET /cocina/pedidos (15/10).
-- ---------------------------------------------------------------------

INSERT INTO permiso (codigo, descripcion) VALUES
    ('admin.gestionar',              'Crear, activar, dar de baja y desbloquear Admins de sus locales (RF-29, RNF-07)'),
    ('empleado.gestionar',           'Empleados, turnos, ausencias, mesas asignadas y blanqueo de PIN (RF-26, RNF-07)'),
    ('menu.gestionar',               'Platos, categorías, etiquetas y destacados de la carta (RF-24, RF-28)'),
    ('mesa.gestionar',               'Mesas, su QR y inhabilitarlas o habilitarlas (RF-25, RF-38)'),
    ('local.configurar',             'Configuración del local: tiempo de inactividad (RNF-14) y regenerar el QR de staff (RF-13)'),
    ('reporte.ver',                  'Reportes de ventas, tiempos de preparación y platos más pedidos (RF-27)'),
    ('auditoria.ver',                'Registro de auditoría del local (RF-27, RF-36)'),
    ('plato.cambiar_disponibilidad', 'Marcar un plato como no disponible o disponible otra vez (RF-20)'),
    ('pedido.cancelar',              'Cancelar un pedido ya aprobado (RF-05, RF-21)'),
    ('pedido.aprobar_rechazar',      'Ver la cola de aprobación y aprobar o rechazar pedidos (RF-14, RF-15)'),
    ('pedido.priorizar',             'Marcar prioridad manual en la cola de cocina (RF-19)'),
    ('cuenta.cerrar',                'Ver las cuentas por cobrar, cerrarlas y reimprimir el ticket (RF-17, RF-18)'),
    ('mesa.ver_asignadas',           'Ver sus mesas asignadas o el salón completo (RF-09)'),
    ('pedido.marcar_servido',        'Marcar un pedido como servido (RF-11)'),
    ('aviso.atender',                'Ver los avisos pendientes y confirmar que los atendió (RF-12)'),
    ('pedido.cambiar_estado_cocina', 'Ver la cola de cocina y pasar pedidos a en preparación o listo (RF-22, RF-23)');


-- ---------------------------------------------------------------------
-- Permisos por rol
-- ---------------------------------------------------------------------

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM (
    SELECT 'GERENTE_DUENO' AS rol, 'admin.gestionar'              AS permiso
    UNION ALL SELECT 'GERENTE_DUENO', 'reporte.ver'
    UNION ALL SELECT 'GERENTE_DUENO', 'auditoria.ver'

    UNION ALL SELECT 'ADMIN', 'empleado.gestionar'
    UNION ALL SELECT 'ADMIN', 'menu.gestionar'
    UNION ALL SELECT 'ADMIN', 'mesa.gestionar'
    UNION ALL SELECT 'ADMIN', 'local.configurar'
    UNION ALL SELECT 'ADMIN', 'reporte.ver'
    UNION ALL SELECT 'ADMIN', 'auditoria.ver'
    UNION ALL SELECT 'ADMIN', 'plato.cambiar_disponibilidad'  -- respaldo de Caja (06/10)
    UNION ALL SELECT 'ADMIN', 'pedido.cancelar'

    UNION ALL SELECT 'MOZO', 'mesa.ver_asignadas'
    UNION ALL SELECT 'MOZO', 'pedido.marcar_servido'
    UNION ALL SELECT 'MOZO', 'aviso.atender'

    UNION ALL SELECT 'CAJA', 'plato.cambiar_disponibilidad'
    UNION ALL SELECT 'CAJA', 'pedido.cancelar'
    UNION ALL SELECT 'CAJA', 'pedido.aprobar_rechazar'
    UNION ALL SELECT 'CAJA', 'pedido.priorizar'
    UNION ALL SELECT 'CAJA', 'cuenta.cerrar'

    UNION ALL SELECT 'COCINA', 'pedido.cambiar_estado_cocina'
) AS m
JOIN rol     r ON r.codigo = m.rol
JOIN permiso p ON p.codigo = m.permiso;
