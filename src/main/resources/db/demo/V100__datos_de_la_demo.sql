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
--   Postman y la tabla del README. Sus mesas siguen hasta la 20, sin mozo.
--   Pizzería La Pala y La Colina, con su personal y 20 mesas cada uno.
--   Las cartas de los cuatro locales (sección 7): La Esquina y El Puerto
--   suman platos a los de dev, La Pala lleva una carta inventada y La Colina
--   su carta real, entera (23 categorías, 189 platos). Cada local tiene dos
--   destacados y dos platos fuera de carta.
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
--
--   Pizzería La Pala y La Colina: el PIN es el local (3 La Pala, 4 La Colina),
--   el rol (1 Mozo, 2 Caja, 3 Cocina) y el número dentro del rol (01 a 05).
--   Rol           Pizzería La Pala                La Colina
--   Gerente/Dueño dueno@lapala.test               dueno@lacolina.test
--   Admin         admin@lapala.test               admin@lacolina.test
--   Caja          Marta 3201 · Nicolás 3202       Inés 4201 · Julián 4202
--   Cocina        Oscar 3301 · Paula 3302         Ramón 4301 · Sofía 4302
--   Mozo 1        Bruno 3101 (mesas 1 a 4)        Rocío 4101
--   Mozo 2        Camila 3102 (5 a 8)             Sergio 4102
--   Mozo 3        Diego 3103 (9 a 12)             Tomás 4103
--   Mozo 4        Florencia 3104 (13 a 16)        Valeria 4104
--   Mozo 5        Lucas 3105 (15 a 18)            Gonzalo 4105
--   Los mozos también entran con su nombre en minúscula y sin tilde como
--   usuario (bruno, rocio, tomas) + contraseña. Las mesas son las mismas en
--   los dos locales; la 19 y la 20 quedan sin mozo.
--
--   Nadie tiene que cambiar la credencial al entrar (debe_cambiar_credencial = FALSE).
--
-- TOKENS (fijos, para que las URL de prueba sirvan siempre)
--   token_staff de La Esquina:       83f39b25-deac-4fde-80cc-4cfdf8117713
--   token_staff de El Puerto:        de076a51-1fbd-46f5-98c5-c895ce59a007
--   token_staff de Pizzería La Pala: d7aa3741-af38-40f2-8bfa-3bba2f2d8e99
--   token_staff de La Colina:        4eeea779-7b24-4857-909f-55f3f889efc8
--   El token_qr de las mesas nuevas está en cada INSERT, al lado de su número.
--   token_pantalla no es fijo: sale del default de la V9.
--   Mesas de La Esquina que vienen de dev:
--     mesa 1: d4964b6b-fdae-4b4f-83e9-1c1e78a21723
--     mesa 2: 7ac79c59-b48d-4db2-8365-5c6a6565d9f4
--     mesa 3: 22f4331e-4464-43b8-a7e2-eea2682b1a40
--     mesa 4: f0c89fba-24a4-4cb4-acff-dee757071597
--     mesa 5: c8b58174-70d8-431f-8b8f-b0d3531c8409
--     mesa 6: 1a8af780-91b5-4815-a6cb-128a6ef92e59
--   Mesas de El Puerto que vienen de dev:
--     mesa 1: ab0124a8-6033-4d73-a1f6-3513140e62f7
--     mesa 2: 9426856c-793f-405c-bc07-46be50499627
--
-- Las mesas 5 a 20 de La Esquina y 3 a 20 de El Puerto no tienen mozo, como
-- las 19 y 20 de los locales nuevos, para MESA_SIN_MOZO. Los mozos
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


-- ---------------------------------------------------------------------
-- 4. Las mesas que faltan en La Esquina (7 a 20) y El Puerto (3 a 20)
-- Quedan sin mozo, así Juan sigue viendo solo sus mesas de dev.
-- ---------------------------------------------------------------------

INSERT INTO mesa (local_id, numero, token_qr) VALUES
    (@esquina, '7', 'a9101e94-fd2a-470e-8345-7886b098f176'),
    (@esquina, '8', '9a48f77f-8fdb-42f8-b37e-32bc784b182c'),
    (@esquina, '9', '3abc56b6-9ca3-4dfd-ada3-d459c148f5f9'),
    (@esquina, '10', 'c6a85b5f-58dd-48ae-bb25-c3c305ecfe54'),
    (@esquina, '11', '28c32028-7d0d-420f-8368-e08acefa36f9'),
    (@esquina, '12', '6eacfac2-1905-4e70-b764-763c044d25d9'),
    (@esquina, '13', '70349c09-0e4c-48f6-80a2-ebae3bae7e79'),
    (@esquina, '14', '838acc0a-0d60-4222-8a7f-52746eccaa1a'),
    (@esquina, '15', '1411e744-4d5a-4ede-b705-2ac183ddbd22'),
    (@esquina, '16', '3ee23e2b-2e7a-4c3d-82ec-81dfedd41299'),
    (@esquina, '17', '3afc2af3-a049-4389-9086-2dc68144795c'),
    (@esquina, '18', 'ec5122a1-3afe-4992-a5a4-ca8e1c9dfda1'),
    (@esquina, '19', '55775c6e-e35a-4e1e-aaf4-96f970b7b150'),
    (@esquina, '20', '63a57323-0271-474b-b38b-3a90fd91bbb6');

INSERT INTO mesa (local_id, numero, token_qr) VALUES
    (@puerto, '3', '596e0328-1822-4e18-90f3-ce192a28b6ba'),
    (@puerto, '4', '305786ef-06f7-4326-992d-113de2df14cc'),
    (@puerto, '5', '8199bf98-5cdb-419b-a750-b50ec7a747d9'),
    (@puerto, '6', 'a6bdc286-2bd5-4a69-abd9-9c6cd9815344'),
    (@puerto, '7', '3dc8cbb1-7709-4feb-b505-11d80a47ada6'),
    (@puerto, '8', 'f336c913-e8c6-417b-930c-13a8b292bd21'),
    (@puerto, '9', 'f6fe3f82-3323-4f13-97d6-814e2dd64c5e'),
    (@puerto, '10', '0cb0c4e2-35d5-4f1f-b64d-3dc8fd4c609f'),
    (@puerto, '11', '0fccf298-692c-4346-b644-deeb15e3992d'),
    (@puerto, '12', '65292bf7-0b67-4564-b34b-2206c59ac8cf'),
    (@puerto, '13', 'a37652f0-ab40-4b8e-9111-882f508152aa'),
    (@puerto, '14', 'a2a75942-b4e5-4411-ae72-597dfdf3cbb2'),
    (@puerto, '15', '28d5dce2-c7bc-4640-ae45-ab9731c6963a'),
    (@puerto, '16', '1a8ba11a-2ad4-4d02-ab28-29e3962d418b'),
    (@puerto, '17', 'd84f46a2-44b1-4c22-aafb-95cea9e20ff7'),
    (@puerto, '18', '67901aaf-4310-4cef-9610-2cbfca2d5dba'),
    (@puerto, '19', '0014ae65-b9aa-4e32-814f-a98a22587c8d'),
    (@puerto, '20', '81322aa7-d238-4a73-a80d-3bc1fd0be691');


-- ---------------------------------------------------------------------
-- 5. Pizzería La Pala
-- PIN: 3 (el local), el rol (1 Mozo, 2 Caja, 3 Cocina) y el número
-- dentro del rol: el mozo 2 es el 3102.
-- ---------------------------------------------------------------------

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (NULL, @rol_dueno, 'Dueño La Pala', NULL, 'dueno@lapala.test', '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', NULL, FALSE);
SET @dueno_pala = LAST_INSERT_ID();

INSERT INTO local (propietario_usuario_id, nombre, direccion, token_staff)
VALUES (@dueno_pala, 'Pizzería La Pala', 'Pasaje Los Tilos 318', 'd7aa3741-af38-40f2-8bfa-3bba2f2d8e99');
SET @pala = LAST_INSERT_ID();

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@pala, @rol_admin, 'Admin La Pala', NULL, 'admin@lapala.test', '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', NULL, FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@pala, LAST_INSERT_ID());

-- Marta: PIN 3201
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@pala, @rol_caja, 'Marta', NULL, NULL, NULL, '$2a$10$S9Ounh6Fan9GU9RqvBS5zucDptzTjVaPtMACG6Q.zBXxvK3kniCb2', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@pala, LAST_INSERT_ID());
-- Nicolás: PIN 3202
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@pala, @rol_caja, 'Nicolás', NULL, NULL, NULL, '$2a$10$b9u/Dxqfz6QMRTnnXV1OUORf2w5J24OisIMMRsjRiiBIBFS71Y5Zi', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@pala, LAST_INSERT_ID());
-- Oscar: PIN 3301
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@pala, @rol_cocina, 'Oscar', NULL, NULL, NULL, '$2a$10$zMFty5DiriBpoQaNfU188.D.n2XB3576QCUF9TyLVZC4K26uYXiQ2', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@pala, LAST_INSERT_ID());
-- Paula: PIN 3302
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@pala, @rol_cocina, 'Paula', NULL, NULL, NULL, '$2a$10$ldt13rzP/BLb9kvKegSJCu3R6BgFoM5HoFjXpHeecrZbk2Y1fhLKy', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@pala, LAST_INSERT_ID());

-- Bruno: PIN 3101, o usuario bruno + contraseña
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@pala, @rol_mozo, 'Bruno', 'bruno', NULL, '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', '$2a$10$ZqJDxBgKyriXzht4yd5llOvIYlcl199yR6aZ7wBz8Y4Fq6hd4LCTa', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@pala, LAST_INSERT_ID());
SET @e_pala_1 = LAST_INSERT_ID();
-- Camila: PIN 3102, o usuario camila + contraseña
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@pala, @rol_mozo, 'Camila', 'camila', NULL, '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', '$2a$10$7mgf/rURyK7Ywv1Yf92JluTp24onb1DP0Ax8UfSGOhL8khN..n.y2', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@pala, LAST_INSERT_ID());
SET @e_pala_2 = LAST_INSERT_ID();
-- Diego: PIN 3103, o usuario diego + contraseña
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@pala, @rol_mozo, 'Diego', 'diego', NULL, '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', '$2a$10$nuGtPaP2n8x0GxN/YX75mO0Wqkv8SLEmrFHXAd5ifHxRvaf4IG6l6', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@pala, LAST_INSERT_ID());
SET @e_pala_3 = LAST_INSERT_ID();
-- Florencia: PIN 3104, o usuario florencia + contraseña
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@pala, @rol_mozo, 'Florencia', 'florencia', NULL, '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', '$2a$10$9vxYJCPsRe.eW6Pxt9BdU.U3PbigiBHhNKMFSdhdtx1d5XjbpVd5S', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@pala, LAST_INSERT_ID());
SET @e_pala_4 = LAST_INSERT_ID();
-- Lucas: PIN 3105, o usuario lucas + contraseña
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@pala, @rol_mozo, 'Lucas', 'lucas', NULL, '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', '$2a$10$apQHCgsViaFrE30np9Itte0fjntMhX6ryH5MGvhpcbT0/RzmabMZS', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@pala, LAST_INSERT_ID());
SET @e_pala_5 = LAST_INSERT_ID();

-- Mesas 1 a 20
INSERT INTO mesa (local_id, numero, token_qr) VALUES
    (@pala, '1', '89e4bbd7-0b91-4d2c-b96b-2d622d37e8df'),
    (@pala, '2', '751646c4-e2b4-4d80-808d-b3bc3236ac8b'),
    (@pala, '3', '2bd4faff-7588-47f5-a5af-e625904bfc32'),
    (@pala, '4', '77e6cf43-c14b-48bc-8a26-4b9b9d3d6c6c'),
    (@pala, '5', 'd64dc4c0-9b17-42cd-952d-0afd9076bab5'),
    (@pala, '6', '1b1d1ea0-efcd-4899-a805-97e2cad82d52'),
    (@pala, '7', '678d6095-0a2a-4340-81dc-a5c11273d274'),
    (@pala, '8', '3dec99d1-9c0f-405d-a46e-447dc671c7eb'),
    (@pala, '9', '22b15db4-8992-4e5c-93d1-9a213dbb61db'),
    (@pala, '10', '3ecbbcd8-fad6-4498-aaeb-c418cb92134e'),
    (@pala, '11', '2898865a-87a7-4126-8135-7d731666bdee'),
    (@pala, '12', 'c613a06d-8442-4625-b08c-f55575d91998'),
    (@pala, '13', '14e8bc42-7a92-49f2-89d4-798ce7512cdc'),
    (@pala, '14', '578daaab-d04a-49e2-8a05-204268e68fe7'),
    (@pala, '15', '59d38688-dfee-49a3-85ab-f04546b868f9'),
    (@pala, '16', '0dd75686-2041-448f-b5f6-268eeb2b9e34'),
    (@pala, '17', 'a4d076ed-c33c-4441-8d6d-6c320581f36c'),
    (@pala, '18', 'a69e28f4-efb8-405d-8c87-7d16390b9a18'),
    (@pala, '19', 'a380fb10-2b94-4c86-a420-8cb1669e1abe'),
    (@pala, '20', '36d7a5dc-3a5b-4ed7-99b2-b624994364a8');

-- Bruno: 1, 2, 3, 4 · Camila: 5, 6, 7, 8 · Diego: 9, 10, 11, 12 · Florencia: 13, 14, 15, 16 · Lucas: 15, 16, 17, 18.
-- La 19 y la 20 quedan sin mozo, para MESA_SIN_MOZO.
INSERT INTO mozo_mesa (local_id, empleado_id, mesa_id)
SELECT @pala, @e_pala_1, id FROM mesa WHERE local_id = @pala AND numero IN ('1', '2', '3', '4')
UNION ALL SELECT @pala, @e_pala_2, id FROM mesa WHERE local_id = @pala AND numero IN ('5', '6', '7', '8')
UNION ALL SELECT @pala, @e_pala_3, id FROM mesa WHERE local_id = @pala AND numero IN ('9', '10', '11', '12')
UNION ALL SELECT @pala, @e_pala_4, id FROM mesa WHERE local_id = @pala AND numero IN ('13', '14', '15', '16')
UNION ALL SELECT @pala, @e_pala_5, id FROM mesa WHERE local_id = @pala AND numero IN ('15', '16', '17', '18');

-- Turnos de los mozos: todos los días, todo el día
INSERT INTO turno (local_id, empleado_id, dia_semana, hora_inicio, hora_fin) VALUES
    (@pala, @e_pala_1, 'LUN', '00:00:00', '23:59:00'),
    (@pala, @e_pala_1, 'MAR', '00:00:00', '23:59:00'),
    (@pala, @e_pala_1, 'MIE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_1, 'JUE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_1, 'VIE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_1, 'SAB', '00:00:00', '23:59:00'),
    (@pala, @e_pala_1, 'DOM', '00:00:00', '23:59:00'),
    (@pala, @e_pala_2, 'LUN', '00:00:00', '23:59:00'),
    (@pala, @e_pala_2, 'MAR', '00:00:00', '23:59:00'),
    (@pala, @e_pala_2, 'MIE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_2, 'JUE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_2, 'VIE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_2, 'SAB', '00:00:00', '23:59:00'),
    (@pala, @e_pala_2, 'DOM', '00:00:00', '23:59:00'),
    (@pala, @e_pala_3, 'LUN', '00:00:00', '23:59:00'),
    (@pala, @e_pala_3, 'MAR', '00:00:00', '23:59:00'),
    (@pala, @e_pala_3, 'MIE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_3, 'JUE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_3, 'VIE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_3, 'SAB', '00:00:00', '23:59:00'),
    (@pala, @e_pala_3, 'DOM', '00:00:00', '23:59:00'),
    (@pala, @e_pala_4, 'LUN', '00:00:00', '23:59:00'),
    (@pala, @e_pala_4, 'MAR', '00:00:00', '23:59:00'),
    (@pala, @e_pala_4, 'MIE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_4, 'JUE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_4, 'VIE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_4, 'SAB', '00:00:00', '23:59:00'),
    (@pala, @e_pala_4, 'DOM', '00:00:00', '23:59:00'),
    (@pala, @e_pala_5, 'LUN', '00:00:00', '23:59:00'),
    (@pala, @e_pala_5, 'MAR', '00:00:00', '23:59:00'),
    (@pala, @e_pala_5, 'MIE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_5, 'JUE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_5, 'VIE', '00:00:00', '23:59:00'),
    (@pala, @e_pala_5, 'SAB', '00:00:00', '23:59:00'),
    (@pala, @e_pala_5, 'DOM', '00:00:00', '23:59:00');


-- ---------------------------------------------------------------------
-- 6. La Colina
-- PIN: 4 (el local), el rol (1 Mozo, 2 Caja, 3 Cocina) y el número
-- dentro del rol: el mozo 2 es el 4102.
-- ---------------------------------------------------------------------

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (NULL, @rol_dueno, 'Dueño La Colina', NULL, 'dueno@lacolina.test', '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', NULL, FALSE);
SET @dueno_colina = LAST_INSERT_ID();

INSERT INTO local (propietario_usuario_id, nombre, direccion, token_staff)
VALUES (@dueno_colina, 'La Colina', 'Camino del Mirador 1200', '4eeea779-7b24-4857-909f-55f3f889efc8');
SET @colina = LAST_INSERT_ID();

INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@colina, @rol_admin, 'Admin La Colina', NULL, 'admin@lacolina.test', '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', NULL, FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@colina, LAST_INSERT_ID());

-- Inés: PIN 4201
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@colina, @rol_caja, 'Inés', NULL, NULL, NULL, '$2a$10$iFvpEUlax9wp3ji/eA3hlecwEfqv8yOpktl5NgYGo52bs6Hnwexga', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@colina, LAST_INSERT_ID());
-- Julián: PIN 4202
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@colina, @rol_caja, 'Julián', NULL, NULL, NULL, '$2a$10$B2pYpSVncWBq0NE1vTXGgeHvo4CZ0FlTUn1xqjizTeVUQM6bqR6.S', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@colina, LAST_INSERT_ID());
-- Ramón: PIN 4301
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@colina, @rol_cocina, 'Ramón', NULL, NULL, NULL, '$2a$10$KIbWAI2x5wMfS4LDIk00QuV3pbnm0Y6UTigKThGl4sgz80t9aa8cK', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@colina, LAST_INSERT_ID());
-- Sofía: PIN 4302
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@colina, @rol_cocina, 'Sofía', NULL, NULL, NULL, '$2a$10$PX2jERiL44e4lFhPuxvTMez0.PZn5XkgjhJgZnV4AjCJtEN.1rjA.', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@colina, LAST_INSERT_ID());

-- Rocío: PIN 4101, o usuario rocio + contraseña
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@colina, @rol_mozo, 'Rocío', 'rocio', NULL, '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', '$2a$10$ApAgOaZMoPuMOh5tykFlQOyq9yOwmHat292ONS7WfJhY9n0xm63Ty', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@colina, LAST_INSERT_ID());
SET @e_colina_1 = LAST_INSERT_ID();
-- Sergio: PIN 4102, o usuario sergio + contraseña
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@colina, @rol_mozo, 'Sergio', 'sergio', NULL, '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', '$2a$10$fZEBKaUMCwTiaphP5VNtouWBAyQynxH/LMB2WS8jfUYErm8k0KUES', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@colina, LAST_INSERT_ID());
SET @e_colina_2 = LAST_INSERT_ID();
-- Tomás: PIN 4103, o usuario tomas + contraseña
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@colina, @rol_mozo, 'Tomás', 'tomas', NULL, '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', '$2a$10$TsXNow8WEjrDJfneMr9zr.X9ttFijoL8idFauwNo/DgQdQVpk5qCS', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@colina, LAST_INSERT_ID());
SET @e_colina_3 = LAST_INSERT_ID();
-- Valeria: PIN 4104, o usuario valeria + contraseña
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@colina, @rol_mozo, 'Valeria', 'valeria', NULL, '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', '$2a$10$aJLZi1v0KRCLWdc3Kmzdz.M/M4HFcsh24i.rik.pNcIBT4vS8fYCW', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@colina, LAST_INSERT_ID());
SET @e_colina_4 = LAST_INSERT_ID();
-- Gonzalo: PIN 4105, o usuario gonzalo + contraseña
INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash, debe_cambiar_credencial)
VALUES (@colina, @rol_mozo, 'Gonzalo', 'gonzalo', NULL, '$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne', '$2a$10$ZwR336BRSU1GDzhP5u8FuOrxLGgLq2S7c.ggMtkjsJTUkP.fDAEjK', FALSE);
INSERT INTO empleado (local_id, usuario_id) VALUES (@colina, LAST_INSERT_ID());
SET @e_colina_5 = LAST_INSERT_ID();

-- Mesas 1 a 20
INSERT INTO mesa (local_id, numero, token_qr) VALUES
    (@colina, '1', '993e499c-556a-42c6-bc4e-75eb0a7be17e'),
    (@colina, '2', 'cb2f5277-c923-412f-8aae-586dd0e99028'),
    (@colina, '3', '72841901-b96d-4300-b0f9-6f7c74aecb2e'),
    (@colina, '4', 'fa2306e2-8fba-4ef5-8007-ddf91a29ad53'),
    (@colina, '5', '77475fef-1303-4791-b23d-d6d99054923e'),
    (@colina, '6', 'ee37858f-d9a7-48a6-bde4-202a793e16c7'),
    (@colina, '7', '1925565e-52c6-45cd-b685-baf9dec99138'),
    (@colina, '8', '0681b7e1-9e45-4cd9-81c5-93f521fe6290'),
    (@colina, '9', 'd5b5bf86-3bf2-420d-8f86-1ba870b2b53c'),
    (@colina, '10', '09b86c7d-a193-456e-8c10-cc5cf9175df5'),
    (@colina, '11', 'ff25c0c1-6aed-408a-b737-3ab5234ced16'),
    (@colina, '12', '4a95c79c-0bbf-4379-92de-ac4b9fe17f74'),
    (@colina, '13', 'f7db499d-d79d-4654-8c02-a720c7196945'),
    (@colina, '14', 'dc713853-b797-4a2d-aa9a-3874540b1eec'),
    (@colina, '15', '0abd4d55-58f1-419a-bf81-3dfc54d1f58c'),
    (@colina, '16', 'd341a48f-75c8-439a-9f9c-4058eccd4197'),
    (@colina, '17', '6dd2be2a-fb35-4a57-b65a-185dd75c1e0f'),
    (@colina, '18', '7193926d-66e5-431f-a3d8-3cbf0b785e94'),
    (@colina, '19', 'f97cab94-c658-4227-b6bc-14da84589803'),
    (@colina, '20', '9006e0f8-615b-483c-ace3-2e611e64ebc8');

-- Rocío: 1, 2, 3, 4 · Sergio: 5, 6, 7, 8 · Tomás: 9, 10, 11, 12 · Valeria: 13, 14, 15, 16 · Gonzalo: 15, 16, 17, 18.
-- La 19 y la 20 quedan sin mozo, para MESA_SIN_MOZO.
INSERT INTO mozo_mesa (local_id, empleado_id, mesa_id)
SELECT @colina, @e_colina_1, id FROM mesa WHERE local_id = @colina AND numero IN ('1', '2', '3', '4')
UNION ALL SELECT @colina, @e_colina_2, id FROM mesa WHERE local_id = @colina AND numero IN ('5', '6', '7', '8')
UNION ALL SELECT @colina, @e_colina_3, id FROM mesa WHERE local_id = @colina AND numero IN ('9', '10', '11', '12')
UNION ALL SELECT @colina, @e_colina_4, id FROM mesa WHERE local_id = @colina AND numero IN ('13', '14', '15', '16')
UNION ALL SELECT @colina, @e_colina_5, id FROM mesa WHERE local_id = @colina AND numero IN ('15', '16', '17', '18');

-- Turnos de los mozos: todos los días, todo el día
INSERT INTO turno (local_id, empleado_id, dia_semana, hora_inicio, hora_fin) VALUES
    (@colina, @e_colina_1, 'LUN', '00:00:00', '23:59:00'),
    (@colina, @e_colina_1, 'MAR', '00:00:00', '23:59:00'),
    (@colina, @e_colina_1, 'MIE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_1, 'JUE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_1, 'VIE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_1, 'SAB', '00:00:00', '23:59:00'),
    (@colina, @e_colina_1, 'DOM', '00:00:00', '23:59:00'),
    (@colina, @e_colina_2, 'LUN', '00:00:00', '23:59:00'),
    (@colina, @e_colina_2, 'MAR', '00:00:00', '23:59:00'),
    (@colina, @e_colina_2, 'MIE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_2, 'JUE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_2, 'VIE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_2, 'SAB', '00:00:00', '23:59:00'),
    (@colina, @e_colina_2, 'DOM', '00:00:00', '23:59:00'),
    (@colina, @e_colina_3, 'LUN', '00:00:00', '23:59:00'),
    (@colina, @e_colina_3, 'MAR', '00:00:00', '23:59:00'),
    (@colina, @e_colina_3, 'MIE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_3, 'JUE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_3, 'VIE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_3, 'SAB', '00:00:00', '23:59:00'),
    (@colina, @e_colina_3, 'DOM', '00:00:00', '23:59:00'),
    (@colina, @e_colina_4, 'LUN', '00:00:00', '23:59:00'),
    (@colina, @e_colina_4, 'MAR', '00:00:00', '23:59:00'),
    (@colina, @e_colina_4, 'MIE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_4, 'JUE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_4, 'VIE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_4, 'SAB', '00:00:00', '23:59:00'),
    (@colina, @e_colina_4, 'DOM', '00:00:00', '23:59:00'),
    (@colina, @e_colina_5, 'LUN', '00:00:00', '23:59:00'),
    (@colina, @e_colina_5, 'MAR', '00:00:00', '23:59:00'),
    (@colina, @e_colina_5, 'MIE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_5, 'JUE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_5, 'VIE', '00:00:00', '23:59:00'),
    (@colina, @e_colina_5, 'SAB', '00:00:00', '23:59:00'),
    (@colina, @e_colina_5, 'DOM', '00:00:00', '23:59:00');


-- ---------------------------------------------------------------------
-- 7. Las cartas de los cuatro locales
-- La Esquina y El Puerto suman platos a los de dev, que quedan con su
-- nombre y su precio; sus categorías de dev solo cambian de orden.
-- La Colina lleva su carta real, entera (chit-go-carta-la-colina en la
-- bóveda): las descripciones son las del menú, y los platos que no la
-- tienen van sin descripción. Sus etiquetas se deducen de los
-- ingredientes que nombra el plato, no de la contaminación cruzada.
-- Las etiquetas se cargan por nombre de plato, dentro de cada local.
-- Las bebidas no llevan etiquetas.
-- ---------------------------------------------------------------------

-- 7.1 La Esquina. Los platos nuevos no llevan Sin TACC ni Sin lactosa,
-- para que valgan los casos CF-01-04 a CF-01-06 de la mesa 1.

INSERT INTO etiqueta (local_id, nombre) VALUES (@esquina, 'Para compartir');
SET @et_esquina_para_compartir = LAST_INSERT_ID();

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@esquina, 'Entradas', 1);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@esquina, @cat, 'Empanada de carne cortada a cuchillo', 'Al horno, con carne cortada a cuchillo, cebolla, huevo y aceituna.', 3800.00, FALSE, TRUE),
    (@esquina, @cat, 'Provoleta a la parrilla', 'Provolone a las brasas con orégano y aceite de oliva.', 9500.00, FALSE, TRUE),
    (@esquina, @cat, 'Rabas', 'Anillas de calamar rebozadas, con limón.', 14000.00, FALSE, FALSE),
    (@esquina, @cat, 'Tortilla de papas', 'Tortilla de papa y cebolla, jugosa, para dos.', 12000.00, FALSE, TRUE);

-- Principales: viene de dev; se suman platos y pasa al orden 2
UPDATE categoria_menu SET orden = 2 WHERE id = @cat_principales;
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@esquina, @cat_principales, 'Bife de chorizo con papas fritas', 'Bife de chorizo a la parrilla, de unos 400 g, con papas fritas.', 28000.00, TRUE, TRUE),
    (@esquina, @cat_principales, 'Suprema a la suiza', 'Suprema de pollo con salsa blanca y queso gratinado, con puré.', 22000.00, FALSE, TRUE),
    (@esquina, @cat_principales, 'Pollo al horno con papas', 'Cuarto de pollo al horno con papas y cebolla.', 19000.00, FALSE, TRUE),
    (@esquina, @cat_principales, 'Matambre a la pizza', 'Matambre de ternera con salsa de tomate, muzzarella y orégano.', 21000.00, FALSE, TRUE),
    (@esquina, @cat_principales, 'Guiso de lentejas', 'Lentejas con chorizo colorado, panceta y verduras. Plato de invierno.', 15000.00, FALSE, FALSE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@esquina, 'Pastas', 3);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@esquina, @cat, 'Ñoquis con tuco', 'Ñoquis de papa caseros con tuco de carne.', 14000.00, FALSE, TRUE),
    (@esquina, @cat, 'Ravioles de ricota con fileto', 'Ravioles caseros de ricota y nuez con salsa fileto.', 15000.00, FALSE, TRUE),
    (@esquina, @cat, 'Sorrentinos de jamón y queso', 'Sorrentinos caseros con salsa a elección: fileto, crema o rosa.', 16000.00, FALSE, TRUE),
    (@esquina, @cat, 'Tallarines al pesto', 'Tallarines caseros con pesto de albahaca y nuez.', 13000.00, FALSE, TRUE),
    (@esquina, @cat, 'Lasaña de verdura', 'Capas de pasta con acelga, ricota, salsa blanca y fileto.', 17000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@esquina, 'Guarniciones', 4);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@esquina, @cat, 'Papas fritas', 'Porción de papas fritas bastón.', 6500.00, FALSE, TRUE),
    (@esquina, @cat, 'Puré de papas', 'Puré de papas con manteca.', 6000.00, FALSE, TRUE),
    (@esquina, @cat, 'Ensalada de rúcula y tomate', 'Rúcula fresca y tomate, con aceite de oliva.', 6500.00, FALSE, TRUE),
    (@esquina, @cat, 'Verduras grilladas', 'Zapallito, berenjena, morrón y cebolla a la plancha.', 8000.00, FALSE, TRUE);

-- Postres: viene de dev; se suman platos y pasa al orden 5
UPDATE categoria_menu SET orden = 5 WHERE id = @cat_postres;
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@esquina, @cat_postres, 'Panqueque con dulce de leche', 'Panqueque caliente relleno de dulce de leche.', 6000.00, FALSE, TRUE),
    (@esquina, @cat_postres, 'Budín de pan', 'Budín de pan casero con caramelo y crema.', 6000.00, FALSE, TRUE),
    (@esquina, @cat_postres, 'Ensalada de frutas', 'Frutas de estación cortadas, en su jugo.', 5500.00, FALSE, TRUE),
    (@esquina, @cat_postres, 'Helado de dos bochas', 'Dos bochas a elección: chocolate, dulce de leche, vainilla o frutilla.', 6000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@esquina, 'Bebidas', 6);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@esquina, @cat, 'Agua mineral', 'Botella de 500 ml, con o sin gas.', 3500.00, FALSE, TRUE),
    (@esquina, @cat, 'Gaseosa', 'Lata de 354 ml: cola, lima limón o naranja.', 4500.00, FALSE, TRUE),
    (@esquina, @cat, 'Limonada', 'Jarra de limonada exprimida, de 1 litro.', 7000.00, FALSE, TRUE),
    (@esquina, @cat, 'Cerveza tirada', 'Pinta de 500 ml, rubia o roja.', 6000.00, FALSE, TRUE),
    (@esquina, @cat, 'Copa de vino de la casa', 'Malbec o blanco de la casa.', 5000.00, FALSE, TRUE);

-- Para compartir: 3 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @esquina, id, @et_esquina_para_compartir FROM plato WHERE local_id = @esquina AND nombre IN (
    'Provoleta a la parrilla',
    'Rabas',
    'Tortilla de papas');

-- Vegano: 3 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @esquina, id, @et_vegano FROM plato WHERE local_id = @esquina AND nombre IN (
    'Papas fritas',
    'Ensalada de rúcula y tomate',
    'Verduras grilladas');

-- 7.2 El Puerto

INSERT INTO etiqueta (local_id, nombre) VALUES (@puerto, 'Sin TACC');
SET @et_puerto_sin_tacc = LAST_INSERT_ID();
INSERT INTO etiqueta (local_id, nombre) VALUES (@puerto, 'Vegano');
SET @et_puerto_vegano = LAST_INSERT_ID();
INSERT INTO etiqueta (local_id, nombre) VALUES (@puerto, 'Sin lactosa');
SET @et_puerto_sin_lactosa = LAST_INSERT_ID();
INSERT INTO etiqueta (local_id, nombre) VALUES (@puerto, 'Con mariscos');
SET @et_puerto_con_mariscos = LAST_INSERT_ID();

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@puerto, 'Entradas', 1);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@puerto, @cat, 'Rabas a la romana', 'Anillas de calamar rebozadas, con limón y alioli.', 16000.00, FALSE, TRUE),
    (@puerto, @cat, 'Cornalitos fritos', 'Porción de cornalitos fritos con limón.', 12000.00, FALSE, FALSE),
    (@puerto, @cat, 'Mejillones a la provenzal', 'Mejillones al vapor con ajo, perejil y vino blanco.', 15000.00, FALSE, TRUE),
    (@puerto, @cat, 'Langostinos al ajillo', 'Langostinos salteados en aceite de oliva, ajo y ají.', 18000.00, FALSE, TRUE),
    (@puerto, @cat, 'Empanada de atún', 'Al horno, con atún, morrón, cebolla y huevo.', 4000.00, FALSE, TRUE);

-- Principales: viene de dev; se suman platos y pasa al orden 2
UPDATE categoria_menu SET orden = 2 WHERE id = @cat_puerto;
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@puerto, @cat_puerto, 'Merluza a la romana con puré', 'Filet de merluza rebozado, con puré de papas.', 19000.00, FALSE, TRUE),
    (@puerto, @cat_puerto, 'Salmón rosado grillado con vegetales', 'Salmón rosado a la plancha con vegetales grillados.', 32000.00, TRUE, TRUE),
    (@puerto, @cat_puerto, 'Abadejo a la manteca negra', 'Filet de abadejo a la plancha con manteca negra y alcaparras, con papas al natural.', 24000.00, FALSE, TRUE),
    (@puerto, @cat_puerto, 'Paella de mariscos', 'Arroz con calamar, mejillones, langostinos y azafrán. Para dos.', 42000.00, TRUE, TRUE),
    (@puerto, @cat_puerto, 'Cazuela de mariscos', 'Mariscos y pescado en salsa de tomate y vino blanco, con pan tostado.', 30000.00, FALSE, TRUE),
    (@puerto, @cat_puerto, 'Trucha a la parrilla', 'Trucha entera a la parrilla con limón y hierbas.', 28000.00, FALSE, FALSE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@puerto, 'Pastas', 3);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@puerto, @cat, 'Sorrentinos de salmón con salsa rosa', 'Sorrentinos caseros rellenos de salmón y ricota.', 19000.00, FALSE, TRUE),
    (@puerto, @cat, 'Tallarines con frutos de mar', 'Tallarines caseros con calamar, mejillones y langostinos en salsa de tomate.', 22000.00, FALSE, TRUE),
    (@puerto, @cat, 'Ñoquis de papa al fileto', 'Ñoquis de papa caseros con salsa fileto.', 14000.00, FALSE, TRUE),
    (@puerto, @cat, 'Raviolones de calabaza con manteca y salvia', 'Raviolones caseros de calabaza y queso, con manteca y salvia.', 16000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@puerto, 'Guarniciones', 4);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@puerto, @cat, 'Papas fritas', 'Porción de papas fritas bastón.', 6500.00, FALSE, TRUE),
    (@puerto, @cat, 'Puré de papas', 'Puré de papas con manteca.', 6000.00, FALSE, TRUE),
    (@puerto, @cat, 'Ensalada mixta', 'Lechuga, tomate y cebolla.', 6000.00, FALSE, TRUE),
    (@puerto, @cat, 'Vegetales grillados', 'Zapallito, berenjena, morrón y cebolla a la plancha.', 8000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@puerto, 'Postres', 5);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@puerto, @cat, 'Flan con dulce de leche', 'Flan casero con dulce de leche.', 6000.00, FALSE, TRUE),
    (@puerto, @cat, 'Helado artesanal', 'Dos bochas a elección.', 6500.00, FALSE, TRUE),
    (@puerto, @cat, 'Ensalada de frutas', 'Frutas de estación cortadas, en su jugo.', 5500.00, FALSE, TRUE),
    (@puerto, @cat, 'Tiramisú', 'Vainillas con café, crema de mascarpone y cacao.', 7500.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@puerto, 'Bebidas', 6);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@puerto, @cat, 'Agua mineral', 'Botella de 500 ml, con o sin gas.', 3500.00, FALSE, TRUE),
    (@puerto, @cat, 'Gaseosa', 'Lata de 354 ml: cola, lima limón o naranja.', 4500.00, FALSE, TRUE),
    (@puerto, @cat, 'Agua saborizada', 'Botella de 500 ml: pomelo, naranja o limón.', 4000.00, FALSE, TRUE),
    (@puerto, @cat, 'Cerveza artesanal', 'Pinta de 500 ml: rubia, roja o negra.', 7000.00, FALSE, TRUE),
    (@puerto, @cat, 'Copa de vino blanco', 'Sauvignon blanc o torrontés.', 5500.00, FALSE, TRUE);

-- Con mariscos: 6 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @puerto, id, @et_puerto_con_mariscos FROM plato WHERE local_id = @puerto AND nombre IN (
    'Rabas a la romana',
    'Mejillones a la provenzal',
    'Langostinos al ajillo',
    'Paella de mariscos',
    'Cazuela de mariscos',
    'Tallarines con frutos de mar');

-- Sin TACC: 11 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @puerto, id, @et_puerto_sin_tacc FROM plato WHERE local_id = @puerto AND nombre IN (
    'Mejillones a la provenzal',
    'Langostinos al ajillo',
    'Salmón rosado grillado con vegetales',
    'Abadejo a la manteca negra',
    'Paella de mariscos',
    'Trucha a la parrilla',
    'Puré de papas',
    'Ensalada mixta',
    'Vegetales grillados',
    'Flan con dulce de leche',
    'Ensalada de frutas');

-- Sin lactosa: 9 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @puerto, id, @et_puerto_sin_lactosa FROM plato WHERE local_id = @puerto AND nombre IN (
    'Mejillones a la provenzal',
    'Langostinos al ajillo',
    'Salmón rosado grillado con vegetales',
    'Paella de mariscos',
    'Trucha a la parrilla',
    'Papas fritas',
    'Ensalada mixta',
    'Vegetales grillados',
    'Ensalada de frutas');

-- Vegano: 4 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @puerto, id, @et_puerto_vegano FROM plato WHERE local_id = @puerto AND nombre IN (
    'Papas fritas',
    'Ensalada mixta',
    'Vegetales grillados',
    'Ensalada de frutas');

-- 7.3 Pizzería La Pala

INSERT INTO etiqueta (local_id, nombre) VALUES (@pala, 'Sin TACC');
SET @et_pala_sin_tacc = LAST_INSERT_ID();
INSERT INTO etiqueta (local_id, nombre) VALUES (@pala, 'Vegano');
SET @et_pala_vegano = LAST_INSERT_ID();
INSERT INTO etiqueta (local_id, nombre) VALUES (@pala, 'Sin lactosa');
SET @et_pala_sin_lactosa = LAST_INSERT_ID();
INSERT INTO etiqueta (local_id, nombre) VALUES (@pala, 'Picante');
SET @et_pala_picante = LAST_INSERT_ID();

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@pala, 'Entradas', 1);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@pala, @cat, 'Fainá', 'Porción de fainá de harina de garbanzo.', 3500.00, FALSE, TRUE),
    (@pala, @cat, 'Provoleta con orégano', 'Provolone al horno de barro con orégano y aceite de oliva.', 9000.00, FALSE, TRUE),
    (@pala, @cat, 'Bastones de muzzarella', 'Seis bastones de muzzarella rebozados, con salsa de tomate.', 8500.00, FALSE, TRUE),
    (@pala, @cat, 'Papas fritas', 'Porción de papas fritas bastón.', 6500.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@pala, 'Pizzas clásicas', 2);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@pala, @cat, 'Pizza muzzarella', 'Salsa de tomate, muzzarella, orégano y aceitunas. Ocho porciones.', 14000.00, FALSE, TRUE),
    (@pala, @cat, 'Pizza napolitana', 'Muzzarella, rodajas de tomate, ajo y perejil.', 16000.00, FALSE, TRUE),
    (@pala, @cat, 'Pizza fugazzeta', 'Rellena de muzzarella, cubierta de cebolla.', 16000.00, FALSE, TRUE),
    (@pala, @cat, 'Pizza fugazza', 'Cebolla, orégano y aceite de oliva, sin queso.', 12000.00, FALSE, TRUE),
    (@pala, @cat, 'Pizza de jamón y morrones', 'Muzzarella, jamón cocido y morrones asados.', 17000.00, FALSE, TRUE),
    (@pala, @cat, 'Pizza calabresa', 'Muzzarella y longaniza calabresa con ají molido.', 17000.00, FALSE, TRUE),
    (@pala, @cat, 'Pizza muzzarella sin TACC', 'Masa sin TACC, salsa de tomate y muzzarella, hecha aparte.', 17000.00, TRUE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@pala, 'Pizzas especiales', 3);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@pala, @cat, 'Pizza La Pala', 'La de la casa: muzzarella, jamón crudo, rúcula y parmesano.', 22000.00, TRUE, TRUE),
    (@pala, @cat, 'Pizza cuatro quesos', 'Muzzarella, roquefort, provolone y parmesano.', 20000.00, FALSE, TRUE),
    (@pala, @cat, 'Pizza margherita', 'Salsa de tomate, muzzarella fresca y albahaca.', 17000.00, FALSE, TRUE),
    (@pala, @cat, 'Pizza de provolone y tomates secos', 'Muzzarella, provolone, tomates secos y orégano.', 19000.00, FALSE, TRUE),
    (@pala, @cat, 'Pizza vegana de vegetales asados', 'Salsa de tomate, berenjena, zapallito, morrón y cebolla asados, sin queso.', 18000.00, FALSE, TRUE),
    (@pala, @cat, 'Pizza diavola', 'Muzzarella, salame picante y ají.', 19000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@pala, 'Empanadas', 4);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@pala, @cat, 'Empanada de carne cortada a cuchillo', 'Al horno de barro, con carne cortada a cuchillo, cebolla y huevo.', 3800.00, FALSE, TRUE),
    (@pala, @cat, 'Empanada de jamón y queso', 'Al horno de barro, con jamón cocido y muzzarella.', 3800.00, FALSE, TRUE),
    (@pala, @cat, 'Empanada de humita', 'Choclo, salsa blanca y queso. De temporada.', 3800.00, FALSE, FALSE),
    (@pala, @cat, 'Empanada caprese', 'Muzzarella, tomate y albahaca.', 3800.00, FALSE, TRUE),
    (@pala, @cat, 'Empanada de carne picante', 'Carne cortada a cuchillo con ají molido.', 3800.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@pala, 'Postres', 5);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@pala, @cat, 'Flan casero', 'Flan casero con dulce de leche o crema.', 5500.00, FALSE, TRUE),
    (@pala, @cat, 'Helado de un cuarto kilo', 'Hasta tres gustos.', 9000.00, FALSE, TRUE),
    (@pala, @cat, 'Tiramisú', 'Vainillas con café, crema de mascarpone y cacao.', 7000.00, FALSE, FALSE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@pala, 'Bebidas', 6);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@pala, @cat, 'Agua mineral', 'Botella de 500 ml, con o sin gas.', 3500.00, FALSE, TRUE),
    (@pala, @cat, 'Gaseosa de 1,5 L', 'Cola, lima limón o naranja.', 7500.00, FALSE, TRUE),
    (@pala, @cat, 'Cerveza de 1 L', 'Botella retornable, rubia o negra.', 9000.00, FALSE, TRUE),
    (@pala, @cat, 'Copa de vino de la casa', 'Malbec o blanco de la casa.', 5000.00, FALSE, TRUE);

-- Vegano: 4 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @pala, id, @et_pala_vegano FROM plato WHERE local_id = @pala AND nombre IN (
    'Fainá',
    'Papas fritas',
    'Pizza fugazza',
    'Pizza vegana de vegetales asados');

-- Sin lactosa: 4 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @pala, id, @et_pala_sin_lactosa FROM plato WHERE local_id = @pala AND nombre IN (
    'Fainá',
    'Papas fritas',
    'Pizza fugazza',
    'Pizza vegana de vegetales asados');

-- Picante: 3 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @pala, id, @et_pala_picante FROM plato WHERE local_id = @pala AND nombre IN (
    'Pizza calabresa',
    'Pizza diavola',
    'Empanada de carne picante');

-- Sin TACC: 2 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @pala, id, @et_pala_sin_tacc FROM plato WHERE local_id = @pala AND nombre IN (
    'Pizza muzzarella sin TACC',
    'Flan casero');

-- 7.4 La Colina: 23 categorías y 189 platos, en el orden del menú

INSERT INTO etiqueta (local_id, nombre) VALUES (@colina, 'Sin TACC');
SET @et_colina_sin_tacc = LAST_INSERT_ID();
INSERT INTO etiqueta (local_id, nombre) VALUES (@colina, 'Vegano');
SET @et_colina_vegano = LAST_INSERT_ID();
INSERT INTO etiqueta (local_id, nombre) VALUES (@colina, 'Sin lactosa');
SET @et_colina_sin_lactosa = LAST_INSERT_ID();

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Entradas', 1);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Empanada de carne', NULL, 4000.00, FALSE, TRUE),
    (@colina, @cat, 'Berenjenas al escabeche', NULL, 5900.00, FALSE, TRUE),
    (@colina, @cat, 'Matambre arrollado casero', NULL, 6900.00, FALSE, TRUE),
    (@colina, @cat, 'Lengua a la vinagreta', NULL, 5700.00, FALSE, TRUE),
    (@colina, @cat, 'Provoleta clásica', NULL, 10000.00, FALSE, TRUE),
    (@colina, @cat, 'Provoleta La Colina', 'Nuestra provoleta a las brasas con rúcula fresca, tomates confitados y lluvia de queso parmesano.', 12000.00, TRUE, TRUE),
    (@colina, @cat, 'Mayonesa de ave', NULL, 9000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Ensaladas', 2);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Zanahoria y huevo', NULL, 6000.00, FALSE, TRUE),
    (@colina, @cat, 'Rúcula y parmesano', NULL, 7500.00, FALSE, TRUE),
    (@colina, @cat, 'Ensalada Mixta', 'Lechuga fresca, tomates y cebolla.', 7500.00, FALSE, TRUE),
    (@colina, @cat, 'Ensalada Completa', 'Mezcla de lechuga, tomate, huevo duro, zanahoria, choclo, arroz, jamón cocido y dados de queso.', 11500.00, FALSE, TRUE),
    (@colina, @cat, 'Ensalada César', 'Tiras de pollo a la plancha, lechuga, queso parmesano, croutones crocantes y salsa César.', 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Ensalada La Colina', 'Colchón de hojas verdes, langostinos, tomates secos y champiñones en su vinagreta.', 20000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Guarniciones', 3);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Tortilla clásica', NULL, 14000.00, FALSE, TRUE),
    (@colina, @cat, 'Papas al natural', NULL, 6800.00, FALSE, TRUE),
    (@colina, @cat, 'Papas fritas clásicas', NULL, 6800.00, FALSE, TRUE),
    (@colina, @cat, 'Papas fritas con cheddar', NULL, 7000.00, FALSE, TRUE),
    (@colina, @cat, 'Papas fritas con cheddar, bacon y verdeo', NULL, 7200.00, FALSE, TRUE),
    (@colina, @cat, 'Puré de papas', NULL, 6800.00, FALSE, TRUE),
    (@colina, @cat, 'Puré mixto', NULL, 7000.00, FALSE, TRUE),
    (@colina, @cat, 'Arroz blanco', NULL, 6000.00, FALSE, TRUE),
    (@colina, @cat, 'Ensalada rusa', NULL, 7000.00, FALSE, TRUE),
    (@colina, @cat, 'Vegetales asados', NULL, 9000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Minutas', 4);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Milanesa de ternera clásica', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 26000.00, FALSE, TRUE),
    (@colina, @cat, 'Milanesa de ternera napolitana', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 30000.00, FALSE, TRUE),
    (@colina, @cat, 'Milanesa de ternera a caballo', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 30000.00, FALSE, TRUE),
    (@colina, @cat, 'Milanesa de pollo clásica', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 22000.00, FALSE, TRUE),
    (@colina, @cat, 'Milanesa de pollo napolitana', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 26000.00, FALSE, TRUE),
    (@colina, @cat, 'Milanesa de pollo a caballo', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 26000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Pastas', 5);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Tallarines', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Canelones de verdura', NULL, 20000.00, FALSE, TRUE),
    (@colina, @cat, 'Lasaña', NULL, 22000.00, FALSE, TRUE),
    (@colina, @cat, 'Sorrentinos de jamón y queso', NULL, 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Ravioles de verdura', NULL, 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Ñoquis', NULL, 16000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Nuestras salsas', 6);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Filetto', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Salsa Blanca', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Salsa Rosa', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Crema', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Bolognesa', NULL, 8000.00, FALSE, TRUE),
    (@colina, @cat, 'Pesto', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Parisienne', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Al Champiñon', NULL, 7500.00, FALSE, TRUE),
    (@colina, @cat, 'Langostinos', NULL, 8500.00, FALSE, TRUE),
    (@colina, @cat, 'Estofado de carne', NULL, 9000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Cocina', 7);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Pollo al Champiñón', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 24000.00, FALSE, TRUE),
    (@colina, @cat, 'Pollo al Verdeo', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 24000.00, FALSE, TRUE),
    (@colina, @cat, 'Pollo a la Mostaza', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 24000.00, FALSE, TRUE),
    (@colina, @cat, 'Bife a la Pimienta', 'Lomo a la pimienta con papines a la crema y hojas verdes.', 36000.00, FALSE, TRUE),
    (@colina, @cat, 'Bife al Champiñón', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 36000.00, FALSE, TRUE),
    (@colina, @cat, 'Bife al Verdeo', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 36000.00, FALSE, TRUE),
    (@colina, @cat, 'Bife a la Mostaza', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 36000.00, FALSE, TRUE),
    (@colina, @cat, 'Bife a la Riojana', 'Con guarnición a elección: papas fritas, puré, ensalada fresca o arroz.', 36000.00, FALSE, TRUE),
    (@colina, @cat, 'Bife de chorizo a las finas hierbas', 'Bife de chorizo con papas rústicas cubierto en salsa de finas hierbas.', 38000.00, FALSE, TRUE),
    (@colina, @cat, 'Ojo de Bife La Colina', 'Ojo de bife con su salsa de fondo de cocción y papas gratinadas.', 34000.00, FALSE, TRUE),
    (@colina, @cat, 'Risotto de Hongos', 'Cremoso risotto de arroz arborio con hongos salteados, queso parmesano, manteca y reducción de vino blanco.', 26000.00, FALSE, TRUE),
    (@colina, @cat, 'Risotto de Pollo', 'Cremoso risotto de arroz arborio con tiernos trozos de pollo dorados, queso parmesano, manteca y reducción de vino blanco.', 26000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Menú ejecutivo', 8);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Menú ejecutivo', 'Entrada + plato principal + bebida o copa de vino + postre o café. Miércoles a viernes al mediodía.', 30000.00, FALSE, FALSE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Al fuego', 9);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Mollejas', NULL, 28000.00, FALSE, TRUE),
    (@colina, @cat, 'Chinchulin', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Chorizo', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Riñon', NULL, 12000.00, FALSE, TRUE),
    (@colina, @cat, 'Riñon a la provenzal', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Morcilla', NULL, 4500.00, FALSE, TRUE),
    (@colina, @cat, 'Asado de tira', NULL, 32000.00, FALSE, TRUE),
    (@colina, @cat, 'Vacío', NULL, 32000.00, FALSE, TRUE),
    (@colina, @cat, 'Ojo de bife', NULL, 28000.00, FALSE, TRUE),
    (@colina, @cat, 'Entraña', NULL, 34000.00, FALSE, TRUE),
    (@colina, @cat, 'Bife de chorizo', NULL, 28000.00, FALSE, TRUE),
    (@colina, @cat, 'Pollo ¼ porción', NULL, 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Matambre de cerdo', NULL, 24000.00, FALSE, TRUE),
    (@colina, @cat, 'Asado al asador', NULL, 36000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Parrillada libre', 10);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Parrillada libre', 'Entrada (empanada de carne), parrillada con guarnición (asado, pollo, vacío, matambre, chorizo, morcilla y achuras) y postre o café, 1 por persona.', 45000.00, TRUE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Menú infantil', 11);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Nuggets de pollo con guarnición', 'Guarnición: papas fritas o puré.', 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Milanesa con guarnición', 'Guarnición: papas fritas o puré.', 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Tallarines con salsa', 'Fideos caseros con salsa filetto, salsa blanca o con manteca y queso.', 18000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Postres', 12);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Flan Casero', NULL, 6000.00, FALSE, TRUE),
    (@colina, @cat, 'Tiramisú', NULL, 7000.00, FALSE, TRUE),
    (@colina, @cat, 'Queso y dulce', NULL, 5000.00, FALSE, TRUE),
    (@colina, @cat, 'Ensalada de frutas', NULL, 5000.00, FALSE, TRUE),
    (@colina, @cat, 'Copa helada', NULL, 7000.00, FALSE, TRUE),
    (@colina, @cat, 'Bombón escocés', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Budín de pan', NULL, 7000.00, FALSE, TRUE),
    (@colina, @cat, 'Almendrado', NULL, 6500.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Cafetería', 13);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Té', NULL, 3500.00, FALSE, TRUE),
    (@colina, @cat, 'Americano', NULL, 4000.00, FALSE, TRUE),
    (@colina, @cat, 'Café con leche', NULL, 4200.00, FALSE, TRUE),
    (@colina, @cat, 'Lágrima', NULL, 4200.00, FALSE, TRUE),
    (@colina, @cat, 'Capuchino', NULL, 5000.00, FALSE, TRUE),
    (@colina, @cat, 'Submarino', NULL, 5500.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Jarras', 14);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Limonada clásica', NULL, 9000.00, FALSE, TRUE),
    (@colina, @cat, 'Limonada menta y jengibre', NULL, 9000.00, FALSE, TRUE),
    (@colina, @cat, 'Limonada de frutos rojos', NULL, 9000.00, FALSE, TRUE),
    (@colina, @cat, 'Naranjada', NULL, 8000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Sin alcohol', 15);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Agua con gas / sin gas', NULL, 4000.00, FALSE, TRUE),
    (@colina, @cat, 'Saborizadas', NULL, 4500.00, FALSE, TRUE),
    (@colina, @cat, 'Gaseosas Linea Coca-Cola', NULL, 5000.00, FALSE, TRUE),
    (@colina, @cat, 'Vaso de jugo exprimido', NULL, 4500.00, FALSE, TRUE),
    (@colina, @cat, 'Cerveza 0.0% 330cc', NULL, 7500.00, FALSE, TRUE),
    (@colina, @cat, 'Cerveza 0.0% 710cc', NULL, 12000.00, FALSE, TRUE),
    (@colina, @cat, 'Energizante', NULL, 6000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Cervezas', 16);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Corona Botella 710cc', NULL, 12000.00, FALSE, TRUE),
    (@colina, @cat, 'Corona Botella 330cc', NULL, 9000.00, FALSE, TRUE),
    (@colina, @cat, 'Stella Artois Botella 330cc', NULL, 9000.00, FALSE, TRUE),
    (@colina, @cat, 'Stella Artois Botella 975cc', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Stella Artois Lata 473cc', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Stella Artois Lata 710cc', NULL, 12000.00, FALSE, TRUE),
    (@colina, @cat, 'Stella Artois Noire Lata 473cc', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Stella Artois Noire Botella 975cc', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Andes origen rubia Lata 473cc', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Andes origen rubia Botella 975cc', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Andes origen roja Lata 473cc', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Patagonia Amber Lager Lata 473cc', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Patagonia Amber Lager Botella 730cc', NULL, 12000.00, FALSE, TRUE),
    (@colina, @cat, 'Patagonia IPA 24.7 Lata 473cc', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Imperial Gold Lata 473cc', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Imperial Gold Botella 1L', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Imperial Botella 1L', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Imperial Stout Botella 1L', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Heineken Lata 473cc', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Heineken Botella 1L', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Warsteiner Lata 473cc', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Warsteiner Botella 1L', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Miller Botella 330cc', NULL, 9000.00, FALSE, TRUE),
    (@colina, @cat, 'Miller Botella 1L', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Quilmes Lata 710cc', NULL, 12000.00, FALSE, TRUE),
    (@colina, @cat, 'Quilmes Botella 1L', NULL, 15000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Vinos recomendados', 17);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Hermandad Malbec', NULL, 42000.00, FALSE, TRUE),
    (@colina, @cat, 'Los Intocables Malbec / Cabernet', NULL, 32000.00, FALSE, TRUE),
    (@colina, @cat, 'Los Helechos Malbec / Cabernet', NULL, 40000.00, FALSE, TRUE),
    (@colina, @cat, 'La Iride Malbec', NULL, 30000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Tintos Malbec', 18);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Vino de la casa Malbec', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Angelica Zapata Malbec', NULL, 45000.00, FALSE, TRUE),
    (@colina, @cat, 'Alta Vista Terroir Malbec', NULL, 22500.00, FALSE, TRUE),
    (@colina, @cat, 'Doña Paula Malbec', NULL, 28000.00, FALSE, TRUE),
    (@colina, @cat, 'Fin del Mundo Malbec', NULL, 24000.00, FALSE, TRUE),
    (@colina, @cat, 'Salentein Reserva Malbec', NULL, 25000.00, FALSE, TRUE),
    (@colina, @cat, 'Salentein Reserva Malbec 375ml', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Salentein Numina Malbec', NULL, 38000.00, FALSE, TRUE),
    (@colina, @cat, 'La Linda Malbec', NULL, 22500.00, FALSE, TRUE),
    (@colina, @cat, 'La Final Malbec', NULL, 20000.00, FALSE, TRUE),
    (@colina, @cat, 'Escorihuela Gascon Gran Reserva Malbec', NULL, 28000.00, FALSE, TRUE),
    (@colina, @cat, 'Las Perdices Reserva Malbec', NULL, 30000.00, FALSE, TRUE),
    (@colina, @cat, 'Las Perdices Reserva Malbec 375ml', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Casillero del Diablo Malbec', NULL, 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Emilia Nieto Senetiner Malbec', NULL, 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Zuccardi Seria A Malbec', NULL, 28000.00, FALSE, TRUE),
    (@colina, @cat, 'Zuccardi Seria Q Malbec', NULL, 35000.00, FALSE, TRUE),
    (@colina, @cat, 'Malcriado Malbec', NULL, 75000.00, FALSE, FALSE),
    (@colina, @cat, 'Luigi Bosca Malbec', NULL, 32000.00, FALSE, TRUE),
    (@colina, @cat, 'Los Intocables Malbec', NULL, 32000.00, FALSE, TRUE),
    (@colina, @cat, 'Mosquita Muerta Black Malbec', NULL, 45000.00, FALSE, TRUE),
    (@colina, @cat, 'Postales Roble Malbec', NULL, 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Trumpeter Malbec', NULL, 28000.00, FALSE, TRUE),
    (@colina, @cat, 'Septima Prestige Malbec', NULL, 45000.00, FALSE, TRUE),
    (@colina, @cat, 'Rutini Malbec', NULL, 48000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Tintos Cabernet', 19);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Vino de la casa Cabernet', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Alta Vista Terroir Cabernet', NULL, 22500.00, FALSE, TRUE),
    (@colina, @cat, 'Doña Paula Cabernet', NULL, 25000.00, FALSE, TRUE),
    (@colina, @cat, 'Fin del Mundo Reserva Cabernet', NULL, 24000.00, FALSE, TRUE),
    (@colina, @cat, 'Salentein Reserva Cabernet', NULL, 25000.00, FALSE, TRUE),
    (@colina, @cat, 'Salentein Reserva Cabernet 375ml', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Salentein Numina Cabernet', NULL, 38000.00, FALSE, TRUE),
    (@colina, @cat, 'Salentein Numina Gran Corte', 'Unión con Malbec.', 45000.00, FALSE, TRUE),
    (@colina, @cat, 'Escorihuela Gascon Gran Reserva Cabernet', NULL, 28000.00, FALSE, TRUE),
    (@colina, @cat, 'Las Perdices Reserva Cabernet', NULL, 30000.00, FALSE, TRUE),
    (@colina, @cat, 'Las Perdices Reserva Cabernet 375ml', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Emilia Nieto Senetiner Cabernet', NULL, 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Los Intocables Cabernet', NULL, 32000.00, FALSE, TRUE),
    (@colina, @cat, 'Mosquita Muerta Black Cabernet', NULL, 45000.00, FALSE, TRUE),
    (@colina, @cat, 'Rutini Cabernet', 'Unión con Malbec.', 42000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Blancos', 20);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Vino de la casa blanco', NULL, 15000.00, FALSE, TRUE),
    (@colina, @cat, 'Cosecha Tardía Norton Dulce', NULL, 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Santa Julia Chenin Dulce', NULL, 18000.00, FALSE, TRUE),
    (@colina, @cat, 'Las Perdices Sauvignon Blanc', NULL, 20000.00, FALSE, TRUE),
    (@colina, @cat, 'Las Perdices Reserva Sauvignon Blanc', NULL, 32000.00, FALSE, TRUE),
    (@colina, @cat, 'Fuego Blanco Sauvignon Blanc', NULL, 25000.00, FALSE, TRUE),
    (@colina, @cat, 'Gran Sombrero Chardonnay', NULL, 28000.00, FALSE, TRUE),
    (@colina, @cat, 'Doña Paula Sauvignon Blanc', NULL, 24000.00, FALSE, TRUE),
    (@colina, @cat, 'Trumpeter Sauvignon Blanc', NULL, 24000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Rosados', 21);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Casa de Herrero Rosé', NULL, 22000.00, FALSE, TRUE),
    (@colina, @cat, 'La Linda Rosé', NULL, 22500.00, FALSE, TRUE),
    (@colina, @cat, 'Luigi Bosca Rosé', NULL, 32000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Espumantes', 22);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Salentein Extra Brut', NULL, 26000.00, FALSE, TRUE),
    (@colina, @cat, 'Escorihuela Gascon Extra Brut', NULL, 28000.00, FALSE, TRUE),
    (@colina, @cat, 'Chandon Delice', NULL, 29000.00, FALSE, TRUE);

INSERT INTO categoria_menu (local_id, nombre, orden) VALUES (@colina, 'Tragos', 23);
SET @cat = LAST_INSERT_ID();
INSERT INTO plato (local_id, categoria_id, nombre, descripcion, precio, destacado, activo) VALUES
    (@colina, @cat, 'Fernet Branca con Cola', NULL, 7000.00, FALSE, TRUE),
    (@colina, @cat, 'Campari con naranja', NULL, 6500.00, FALSE, TRUE),
    (@colina, @cat, 'Gancia', NULL, 6500.00, FALSE, TRUE);

-- Sin TACC: 28 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @colina, id, @et_colina_sin_tacc FROM plato WHERE local_id = @colina AND nombre IN (
    'Berenjenas al escabeche',
    'Lengua a la vinagreta',
    'Provoleta clásica',
    'Provoleta La Colina',
    'Zanahoria y huevo',
    'Rúcula y parmesano',
    'Ensalada Mixta',
    'Ensalada La Colina',
    'Tortilla clásica',
    'Papas al natural',
    'Puré de papas',
    'Puré mixto',
    'Arroz blanco',
    'Vegetales asados',
    'Mollejas',
    'Chinchulin',
    'Riñon',
    'Riñon a la provenzal',
    'Asado de tira',
    'Vacío',
    'Ojo de bife',
    'Entraña',
    'Bife de chorizo',
    'Pollo ¼ porción',
    'Matambre de cerdo',
    'Asado al asador',
    'Flan Casero',
    'Ensalada de frutas');

-- Vegano: 5 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @colina, id, @et_colina_vegano FROM plato WHERE local_id = @colina AND nombre IN (
    'Berenjenas al escabeche',
    'Ensalada Mixta',
    'Papas al natural',
    'Vegetales asados',
    'Ensalada de frutas');

-- Sin lactosa: 23 platos
INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id)
SELECT @colina, id, @et_colina_sin_lactosa FROM plato WHERE local_id = @colina AND nombre IN (
    'Berenjenas al escabeche',
    'Lengua a la vinagreta',
    'Mayonesa de ave',
    'Zanahoria y huevo',
    'Ensalada Mixta',
    'Ensalada La Colina',
    'Tortilla clásica',
    'Papas al natural',
    'Ensalada rusa',
    'Vegetales asados',
    'Mollejas',
    'Chinchulin',
    'Riñon',
    'Riñon a la provenzal',
    'Asado de tira',
    'Vacío',
    'Ojo de bife',
    'Entraña',
    'Bife de chorizo',
    'Pollo ¼ porción',
    'Matambre de cerdo',
    'Asado al asador',
    'Ensalada de frutas');
