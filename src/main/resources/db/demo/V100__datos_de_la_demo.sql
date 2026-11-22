-- =====================================================================
-- Chit&Go — datos de la demo (semilla de la entrega, ST-28)
-- Solo corre con el perfil demo (application-demo.yml), que suma db/demo a las
-- locations de Flyway. No se combina con dev.
--
-- Es una migración versionada: Flyway la corre una sola vez, sobre una base
-- vacía, y la deja anotada en flyway_schema_history. No vuelve a correr al
-- reiniciar el back, y si se borran datos no reaparecen. Por eso no borra nada
-- al empezar, al revés que la semilla de dev. Si la base ya tiene locales,
-- FrenoDeLaSemillaDemo no deja arrancar el back antes de llegar acá.
--
-- Va con el número 100 para quedar después de todas las migraciones del
-- esquema. Si se edita después de aplicada, Flyway falla al validar la suma de
-- control: la base se recrea (docker compose down -v, o DROP DATABASE).
--
-- No usa ids fijos: busca lo que necesita por código o con LAST_INSERT_ID().
--
-- CONTENIDO
--   La Esquina y El Puerto, con los mismos usuarios, PIN, tokens y carta que
--   la semilla de dev (db/dev/R__datos_de_prueba.sql), así sirven los casos de
--   Postman y la tabla del README.
--
-- CREDENCIALES (solo para la demo)
--   Contraseña de todos los que entran con contraseña: Chitgo2026!
--   Rol          Nombre            Entra con
--   Gerente/Dueño Dueño La Esquina  dueno@laesquina.test + contraseña
--   Admin        Admin La Esquina  admin@laesquina.test + contraseña
--   Mozo         Juan              PIN 1234, o usuario juan + contraseña (mesas 1, 2 y 3)
--   Mozo         Ana               PIN 5678, o usuario ana + contraseña (mesas 3 y 4)
--   Caja         Carla             PIN 2468
--   Cocina       Hugo              PIN 1357
--   El Puerto:   dueno@elpuerto.test + contraseña · mozo juan, PIN 4321 o usuario juan + contraseña
--   Nadie tiene que cambiar la credencial al entrar (debe_cambiar_credencial = FALSE).
--
-- TOKENS (fijos, para que las URL de prueba sirvan siempre)
--   token_staff de La Esquina: 83f39b25-deac-4fde-80cc-4cfdf8117713
--   token_staff de El Puerto:  de076a51-1fbd-46f5-98c5-c895ce59a007
--   Mesas de La Esquina:
--     mesa 1: d4964b6b-fdae-4b4f-83e9-1c1e78a21723
--     mesa 2: 7ac79c59-b48d-4db2-8365-5c6a6565d9f4
--     mesa 3: 22f4331e-4464-43b8-a7e2-eea2682b1a40
--     mesa 4: f0c89fba-24a4-4cb4-acff-dee757071597
--     mesa 5: c8b58174-70d8-431f-8b8f-b0d3531c8409
--     mesa 6: 1a8af780-91b5-4815-a6cb-128a6ef92e59
--   Mesas de El Puerto:
--     mesa 1: ab0124a8-6033-4d73-a1f6-3513140e62f7
--     mesa 2: 9426856c-793f-405c-bc07-46be50499627
--
-- Las mesas 5 y 6 de La Esquina no tienen mozo, para MESA_SIN_MOZO. Los mozos
-- tienen turno todos los días de 00:00 a 23:59.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Roles (los carga la V2; acá solo se buscan sus ids)
-- ---------------------------------------------------------------------

SET @rol_dueno = (SELECT id FROM rol WHERE codigo = 'GERENTE_DUENO');
SET @rol_admin = (SELECT id FROM rol WHERE codigo = 'ADMIN');
SET @rol_mozo  = (SELECT id FROM rol WHERE codigo = 'MOZO');
SET @rol_caja  = (SELECT id FROM rol WHERE codigo = 'CAJA');
SET @rol_cocina = (SELECT id FROM rol WHERE codigo = 'COCINA');


-- ---------------------------------------------------------------------
-- 2. La Esquina
-- usuario y local se apuntan entre sí: primero el Gerente/Dueño (sin local),
-- después el local y después el resto.
-- ---------------------------------------------------------------------

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (NULL, @rol_dueno, 'Dueño La Esquina', NULL, 'dueno@laesquina.test', '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', NULL, FALSE);
SET @dueno_esquina = LAST_INSERT_ID();

INSERT INTO local (propietario_usuario_id, nombre, token_staff)
VALUES (@dueno_esquina, 'La Esquina', '83f39b25-deac-4fde-80cc-4cfdf8117713');
SET @esquina = LAST_INSERT_ID();

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@esquina, @rol_admin, 'Admin La Esquina', NULL, 'admin@laesquina.test', '$2a$10$rijw8LV8uA2zbKVVeyrPyO4.b1Ltv87kV57h9IMFG8DIWNOHlXBTa', NULL, FALSE);
SET @u_admin = LAST_INSERT_ID();

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@esquina, @rol_mozo, 'Juan', 'juan', NULL, '$2a$10$Tgau6DpsbPnFK5L3ti4KpeIp/cQNrJUxPU45TEgJrLmHXYhytEhhu', '$2a$10$t2Q1Q8gud.hYJMxAsBRp4ek5rx8/sWGJ.6C6ft1i8lFprl.zN5HnW', FALSE);
SET @u_juan = LAST_INSERT_ID();

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@esquina, @rol_mozo, 'Ana', 'ana', NULL, '$2a$10$5wwCgcVES7lPoPRyCO.SmujsbTTV0/7jtPxdm.hbaMLZ8RvRtZNgu', '$2a$10$yAghgxi/.X69KdAITYrma.xtOVCB41NUDFAMMSzP5n/DSpE0.2phi', FALSE);
SET @u_ana = LAST_INSERT_ID();

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@esquina, @rol_caja, 'Carla', NULL, NULL, NULL, '$2a$10$BOamMhfZzWLrnVrYkHTLwuAbeMj1.Xjuk01SpC9FoJIhb87k4W/cG', FALSE);
SET @u_carla = LAST_INSERT_ID();

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@esquina, @rol_cocina, 'Hugo', NULL, NULL, NULL, '$2a$10$5S4m7lli6BngexCywm1R8u9IbIqjolaoXCTv90OhSXjhBE9c22pIK', FALSE);
SET @u_hugo = LAST_INSERT_ID();

-- Empleados: todos los usuarios de un local menos el Gerente/Dueño
INSERT INTO empleado (local_id, usuario_id) VALUES (@esquina, @u_admin);
INSERT INTO empleado (local_id, usuario_id) VALUES (@esquina, @u_juan);
SET @e_juan = LAST_INSERT_ID();
INSERT INTO empleado (local_id, usuario_id) VALUES (@esquina, @u_ana);
SET @e_ana = LAST_INSERT_ID();
INSERT INTO empleado (local_id, usuario_id) VALUES (@esquina, @u_carla);
INSERT INTO empleado (local_id, usuario_id) VALUES (@esquina, @u_hugo);

-- Mesas 1 a 6
INSERT INTO mesa (local_id, numero, token_qr) VALUES
    (@esquina, '1', 'd4964b6b-fdae-4b4f-83e9-1c1e78a21723'),
    (@esquina, '2', '7ac79c59-b48d-4db2-8365-5c6a6565d9f4'),
    (@esquina, '3', '22f4331e-4464-43b8-a7e2-eea2682b1a40'),
    (@esquina, '4', 'f0c89fba-24a4-4cb4-acff-dee757071597'),
    (@esquina, '5', 'c8b58174-70d8-431f-8b8f-b0d3531c8409'),
    (@esquina, '6', '1a8af780-91b5-4815-a6cb-128a6ef92e59');

SET @m1 = (SELECT id FROM mesa WHERE local_id = @esquina AND numero = '1');
SET @m2 = (SELECT id FROM mesa WHERE local_id = @esquina AND numero = '2');
SET @m3 = (SELECT id FROM mesa WHERE local_id = @esquina AND numero = '3');
SET @m4 = (SELECT id FROM mesa WHERE local_id = @esquina AND numero = '4');

-- Juan: mesas 1, 2 y 3 · Ana: mesas 3 y 4 (la 3 la tienen los dos)
INSERT INTO mozo_mesa (local_id, empleado_id, mesa_id) VALUES
    (@esquina, @e_juan, @m1),
    (@esquina, @e_juan, @m2),
    (@esquina, @e_juan, @m3),
    (@esquina, @e_ana,  @m3),
    (@esquina, @e_ana,  @m4);

-- Turnos de los mozos: todos los días, todo el día
INSERT INTO turno (local_id, empleado_id, dia_semana, hora_inicio, hora_fin) VALUES
    (@esquina, @e_juan, 'LUN', '00:00:00', '23:59:00'),
    (@esquina, @e_juan, 'MAR', '00:00:00', '23:59:00'),
    (@esquina, @e_juan, 'MIE', '00:00:00', '23:59:00'),
    (@esquina, @e_juan, 'JUE', '00:00:00', '23:59:00'),
    (@esquina, @e_juan, 'VIE', '00:00:00', '23:59:00'),
    (@esquina, @e_juan, 'SAB', '00:00:00', '23:59:00'),
    (@esquina, @e_juan, 'DOM', '00:00:00', '23:59:00'),
    (@esquina, @e_ana, 'LUN', '00:00:00', '23:59:00'),
    (@esquina, @e_ana, 'MAR', '00:00:00', '23:59:00'),
    (@esquina, @e_ana, 'MIE', '00:00:00', '23:59:00'),
    (@esquina, @e_ana, 'JUE', '00:00:00', '23:59:00'),
    (@esquina, @e_ana, 'VIE', '00:00:00', '23:59:00'),
    (@esquina, @e_ana, 'SAB', '00:00:00', '23:59:00'),
    (@esquina, @e_ana, 'DOM', '00:00:00', '23:59:00');

-- Carta
INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@esquina, 'Principales', 1);
SET @cat_principales = LAST_INSERT_ID();
INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@esquina, 'Postres', 2);
SET @cat_postres = LAST_INSERT_ID();

INSERT INTO etiqueta (local_id, nombre) VALUES (@esquina, 'Sin TACC');
SET @et_sin_tacc = LAST_INSERT_ID();
INSERT INTO etiqueta (local_id, nombre) VALUES (@esquina, 'Vegano');
SET @et_vegano = LAST_INSERT_ID();
INSERT INTO etiqueta (local_id, nombre) VALUES (@esquina, 'Sin lactosa');

INSERT INTO plato (local_id, categoria_id, nombre, precio, destacado) VALUES (@esquina, @cat_principales, 'Milanesa napolitana', 9500.00, FALSE);
SET @pl_milanesa = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, precio, destacado) VALUES (@esquina, @cat_principales, 'Ensalada mixta', 6000.00, FALSE);
SET @pl_ensalada = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, precio, destacado) VALUES (@esquina, @cat_postres, 'Flan casero', 4000.00, TRUE);
SET @pl_flan = LAST_INSERT_ID();

INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id) VALUES
    (@esquina, @pl_ensalada, @et_sin_tacc),
    (@esquina, @pl_ensalada, @et_vegano),
    (@esquina, @pl_flan,     @et_sin_tacc);


-- ---------------------------------------------------------------------
-- 3. El Puerto (segundo local, para ver que los datos no se mezclan, RNF-10)
-- ---------------------------------------------------------------------

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (NULL, @rol_dueno, 'Dueño El Puerto', NULL, 'dueno@elpuerto.test', '$2a$10$XHAmSDeLoX2JTVBT9NeCfeJL4xUJCKNKbDpt0a.GDAdoVi9bweKyW', NULL, FALSE);
SET @dueno_puerto = LAST_INSERT_ID();

INSERT INTO local (propietario_usuario_id, nombre, token_staff)
VALUES (@dueno_puerto, 'El Puerto', 'de076a51-1fbd-46f5-98c5-c895ce59a007');
SET @puerto = LAST_INSERT_ID();

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@puerto, @rol_mozo, 'Juan', 'juan', NULL, '$2a$10$xgcTWytN2/ahOi2GZPNoD.4Hxri61bhx1.rwrK38yyrny2I3/6nLK', '$2a$10$D72GFyoYD/A4W.NGLJQdIelpDo329OFp8cw6y/OmIEQHML103mttG', FALSE);
SET @u_juan_puerto = LAST_INSERT_ID();

INSERT INTO empleado (local_id, usuario_id) VALUES (@puerto, @u_juan_puerto);
SET @e_juan_puerto = LAST_INSERT_ID();

INSERT INTO mesa (local_id, numero, token_qr) VALUES
    (@puerto, '1', 'ab0124a8-6033-4d73-a1f6-3513140e62f7'),
    (@puerto, '2', '9426856c-793f-405c-bc07-46be50499627');

SET @pm1 = (SELECT id FROM mesa WHERE local_id = @puerto AND numero = '1');
SET @pm2 = (SELECT id FROM mesa WHERE local_id = @puerto AND numero = '2');

INSERT INTO mozo_mesa (local_id, empleado_id, mesa_id) VALUES
    (@puerto, @e_juan_puerto, @pm1),
    (@puerto, @e_juan_puerto, @pm2);

INSERT INTO turno (local_id, empleado_id, dia_semana, hora_inicio, hora_fin) VALUES
    (@puerto, @e_juan_puerto, 'LUN', '00:00:00', '23:59:00'),
    (@puerto, @e_juan_puerto, 'MAR', '00:00:00', '23:59:00'),
    (@puerto, @e_juan_puerto, 'MIE', '00:00:00', '23:59:00'),
    (@puerto, @e_juan_puerto, 'JUE', '00:00:00', '23:59:00'),
    (@puerto, @e_juan_puerto, 'VIE', '00:00:00', '23:59:00'),
    (@puerto, @e_juan_puerto, 'SAB', '00:00:00', '23:59:00'),
    (@puerto, @e_juan_puerto, 'DOM', '00:00:00', '23:59:00');

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@puerto, 'Principales', 1);
SET @cat_puerto = LAST_INSERT_ID();

INSERT INTO plato (local_id, categoria_id, nombre, precio, destacado)
VALUES (@puerto, @cat_puerto, 'Pizza muzzarella', 8000.00, FALSE);
