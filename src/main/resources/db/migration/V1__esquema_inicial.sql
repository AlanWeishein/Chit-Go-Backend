-- =====================================================================
-- Chit&Go — V1: esquema inicial (MySQL 8.0.16 o más nuevo, el Compose usa 8.4)
-- Sale del DER v3 de Figma, con lo ajustado y decidido el 2026-10-13.
-- Va en chit-go-back/src/main/resources/db/migration/ y lo corre Flyway
-- al levantar el back. Hibernate está en ddl-auto: validate, así que
-- no crea nada: solo revisa que las entidades coincidan con esto.
--
-- Reglas que no entran acá (se validan en el Service): están en el panel
-- "Reglas fuera del DDL" del DER y en chit-go-esquema-db.
-- =====================================================================


-- ---------------------------------------------------------------------
-- Roles y permisos (datos fijos, sin local_id)
-- ---------------------------------------------------------------------

CREATE TABLE rol (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    codigo  VARCHAR(30)  NOT NULL,  -- GERENTE_DUENO, ADMIN, MOZO, CAJA, COCINA
    nombre  VARCHAR(60)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_rol_codigo UNIQUE (codigo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE permiso (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    codigo       VARCHAR(60)   NOT NULL,  -- ej. pedido.aprobar_rechazar
    descripcion  VARCHAR(255)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_permiso_codigo UNIQUE (codigo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE rol_permiso (
    rol_id      BIGINT NOT NULL,
    permiso_id  BIGINT NOT NULL,
    PRIMARY KEY (rol_id, permiso_id),
    CONSTRAINT fk_rol_permiso_rol     FOREIGN KEY (rol_id)     REFERENCES rol (id),
    CONSTRAINT fk_rol_permiso_permiso FOREIGN KEY (permiso_id) REFERENCES permiso (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------
-- Usuario y local
-- usuario.local_id y local.propietario_usuario_id se apuntan entre sí.
-- usuario se crea primero sin la FK a local, y la FK se suma al final
-- de esta sección. Al cargar datos: primero el Gerente/Dueño (local_id
-- en NULL), después el local y después el resto de los usuarios.
-- ---------------------------------------------------------------------

CREATE TABLE usuario (
    id                       BIGINT            NOT NULL AUTO_INCREMENT,
    local_id                 BIGINT            NULL,      -- NULL solo para GERENTE_DUENO
    rol_id                   BIGINT            NOT NULL,
    nombre                   VARCHAR(120)      NOT NULL,
    nombre_usuario           VARCHAR(40)       NULL,      -- login del Mozo con usuario y contraseña
    email                    VARCHAR(160)      NULL,      -- Admin y Gerente/Dueño
    hash_password            VARCHAR(100)      NULL,      -- BCrypt
    pin_hash                 VARCHAR(100)      NULL,      -- BCrypt
    intentos_fallidos        TINYINT UNSIGNED  NOT NULL DEFAULT 0,  -- RNF-07: se bloquea a los 5
    bloqueado_hasta          TIMESTAMP         NULL,
    debe_cambiar_credencial  BOOLEAN           NOT NULL DEFAULT TRUE,
    activo                   BOOLEAN           NOT NULL DEFAULT TRUE,
    creado_en                TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_usuario_email          UNIQUE (email),
    CONSTRAINT uq_usuario_nombre_usuario UNIQUE (local_id, nombre_usuario),
    INDEX idx_usuario_local_rol_activo (local_id, rol_id, activo),
    CONSTRAINT fk_usuario_rol FOREIGN KEY (rol_id) REFERENCES rol (id),
    -- Qué credenciales lleva cada rol lo controla el Service (el rol está en otra tabla)
    CONSTRAINT chk_usuario_credencial CHECK (pin_hash IS NOT NULL OR hash_password IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE local (
    id                          BIGINT        NOT NULL AUTO_INCREMENT,
    propietario_usuario_id      BIGINT        NOT NULL,  -- el Gerente/Dueño (RF-29)
    nombre                      VARCHAR(120)  NOT NULL,
    direccion                   VARCHAR(255)  NULL,
    token_staff                 CHAR(36)      NOT NULL,  -- UUID del QR de staff (RF-13)
    minutos_inactividad_sesion  SMALLINT      NOT NULL DEFAULT 10,  -- RNF-14
    creado_en                   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_local_token_staff UNIQUE (token_staff),
    CONSTRAINT fk_local_propietario FOREIGN KEY (propietario_usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE usuario
    ADD CONSTRAINT fk_usuario_local FOREIGN KEY (local_id) REFERENCES local (id);


-- ---------------------------------------------------------------------
-- Personal: empleado, turnos y ausencias
-- ---------------------------------------------------------------------

CREATE TABLE empleado (
    id          BIGINT     NOT NULL AUTO_INCREMENT,
    local_id    BIGINT     NOT NULL,
    usuario_id  BIGINT     NOT NULL,
    creado_en   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_empleado_usuario UNIQUE (usuario_id),
    INDEX idx_empleado_local (local_id),
    CONSTRAINT fk_empleado_local   FOREIGN KEY (local_id)   REFERENCES local (id),
    CONSTRAINT fk_empleado_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE turno (
    id           BIGINT  NOT NULL AUTO_INCREMENT,
    local_id     BIGINT  NOT NULL,
    empleado_id  BIGINT  NOT NULL,
    dia_semana   ENUM('LUN','MAR','MIE','JUE','VIE','SAB','DOM') NOT NULL,
    hora_inicio  TIME    NOT NULL,
    hora_fin     TIME    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_turno_empleado_dia (empleado_id, dia_semana),
    CONSTRAINT fk_turno_local    FOREIGN KEY (local_id)    REFERENCES local (id),
    CONSTRAINT fk_turno_empleado FOREIGN KEY (empleado_id) REFERENCES empleado (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ausencia (
    id                     BIGINT     NOT NULL AUTO_INCREMENT,
    local_id               BIGINT     NOT NULL,
    empleado_id            BIGINT     NOT NULL,
    tipo                   ENUM('VACACIONES','FRANCO','BAJA_TEMPORAL') NOT NULL,
    desde                  DATE       NOT NULL,
    hasta                  DATE       NULL,      -- sin fecha de vuelta: se reactiva a mano
    creada_por_usuario_id  BIGINT     NOT NULL,  -- el Admin que la cargó
    creado_en              TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_ausencia_empleado_fechas (empleado_id, desde, hasta),
    CONSTRAINT fk_ausencia_local      FOREIGN KEY (local_id)              REFERENCES local (id),
    CONSTRAINT fk_ausencia_empleado   FOREIGN KEY (empleado_id)           REFERENCES empleado (id) ON DELETE CASCADE,
    CONSTRAINT fk_ausencia_creada_por FOREIGN KEY (creada_por_usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------
-- Mesas
-- ---------------------------------------------------------------------

CREATE TABLE mesa (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    local_id      BIGINT       NOT NULL,
    numero        VARCHAR(20)  NOT NULL,
    token_qr      CHAR(36)     NOT NULL,  -- UUID que va en el QR, no el número
    inhabilitada  BOOLEAN      NOT NULL DEFAULT FALSE,  -- RF-38
    creado_en     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_mesa_token_qr UNIQUE (token_qr),
    CONSTRAINT uq_mesa_numero   UNIQUE (local_id, numero),
    CONSTRAINT fk_mesa_local FOREIGN KEY (local_id) REFERENCES local (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Mesas asignadas a cada mozo (el empleado tiene que tener rol MOZO: lo valida el Service)
CREATE TABLE mozo_mesa (
    empleado_id  BIGINT NOT NULL,
    mesa_id      BIGINT NOT NULL,
    PRIMARY KEY (empleado_id, mesa_id),
    CONSTRAINT fk_mozo_mesa_empleado FOREIGN KEY (empleado_id) REFERENCES empleado (id) ON DELETE CASCADE,
    CONSTRAINT fk_mozo_mesa_mesa     FOREIGN KEY (mesa_id)     REFERENCES mesa (id)     ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------
-- Carta
-- ---------------------------------------------------------------------

CREATE TABLE categoria_menu (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    local_id  BIGINT       NOT NULL,
    nombre    VARCHAR(80)  NOT NULL,
    orden     SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_categoria_menu_local FOREIGN KEY (local_id) REFERENCES local (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE plato (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    local_id      BIGINT         NOT NULL,
    categoria_id  BIGINT         NOT NULL,
    nombre        VARCHAR(120)   NOT NULL,
    descripcion   VARCHAR(500)   NULL,
    precio        DECIMAL(10,2)  NOT NULL,
    imagen_url    VARCHAR(255)   NULL,
    destacado     BOOLEAN        NOT NULL DEFAULT FALSE,  -- RF-28
    activo        BOOLEAN        NOT NULL DEFAULT TRUE,   -- FALSE = no disponible o fuera de carta, nunca se borra
    creado_en     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_plato_local_categoria_activo (local_id, categoria_id, activo),
    CONSTRAINT fk_plato_local     FOREIGN KEY (local_id)     REFERENCES local (id),
    CONSTRAINT fk_plato_categoria FOREIGN KEY (categoria_id) REFERENCES categoria_menu (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE etiqueta (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    local_id  BIGINT       NOT NULL,
    nombre    VARCHAR(60)  NOT NULL,  -- ej. Sin TACC, Vegano
    PRIMARY KEY (id),
    CONSTRAINT uq_etiqueta_nombre UNIQUE (local_id, nombre),
    CONSTRAINT fk_etiqueta_local FOREIGN KEY (local_id) REFERENCES local (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE plato_etiqueta (
    plato_id     BIGINT NOT NULL,
    etiqueta_id  BIGINT NOT NULL,
    PRIMARY KEY (plato_id, etiqueta_id),
    CONSTRAINT fk_plato_etiqueta_plato    FOREIGN KEY (plato_id)    REFERENCES plato (id)    ON DELETE CASCADE,
    CONSTRAINT fk_plato_etiqueta_etiqueta FOREIGN KEY (etiqueta_id) REFERENCES etiqueta (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------
-- Cuenta, sesiones del cliente, pedidos y ticket
-- La mesa no guarda estado: está libre si no tiene una cuenta distinta de CERRADA.
-- Al abrir una cuenta se bloquea la fila de la mesa (SELECT ... FOR UPDATE).
-- ---------------------------------------------------------------------

CREATE TABLE cuenta (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    local_id                BIGINT         NOT NULL,
    mesa_id                 BIGINT         NOT NULL,
    mozo_id                 BIGINT         NULL,      -- mozo de turno al abrir, NULL si no había (aviso MESA_SIN_MOZO)
    estado                  ENUM('ABIERTA','SOLICITADA','CERRADA') NOT NULL DEFAULT 'ABIERTA',
    abierta_en              TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    solicitada_en           TIMESTAMP      NULL,
    cerrada_en              TIMESTAMP      NULL,
    cerrada_por_usuario_id  BIGINT         NULL,      -- Caja (RF-17)
    total                   DECIMAL(10,2)  NULL,      -- se fija al cerrar
    version                 INT            NOT NULL DEFAULT 0,  -- optimistic locking (@Version), RNF-01
    PRIMARY KEY (id),
    INDEX idx_cuenta_mesa_estado (mesa_id, estado),
    CONSTRAINT fk_cuenta_local       FOREIGN KEY (local_id)               REFERENCES local (id),
    CONSTRAINT fk_cuenta_mesa        FOREIGN KEY (mesa_id)                REFERENCES mesa (id),
    CONSTRAINT fk_cuenta_mozo        FOREIGN KEY (mozo_id)                REFERENCES empleado (id),
    CONSTRAINT fk_cuenta_cerrada_por FOREIGN KEY (cerrada_por_usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE cliente_sesion (
    id            BIGINT     NOT NULL AUTO_INCREMENT,
    local_id      BIGINT     NOT NULL,
    cuenta_id     BIGINT     NOT NULL,
    token_sesion  CHAR(36)   NOT NULL,  -- UUID del celular (header X-Session-Token)
    es_iniciador  BOOLEAN    NOT NULL DEFAULT FALSE,  -- D-03: una sola por cuenta, la controla el Service
    creado_en     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_cliente_sesion_token UNIQUE (token_sesion),
    CONSTRAINT fk_cliente_sesion_local  FOREIGN KEY (local_id)  REFERENCES local (id),
    CONSTRAINT fk_cliente_sesion_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuenta (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE pedido (
    id                        BIGINT     NOT NULL AUTO_INCREMENT,
    local_id                  BIGINT     NOT NULL,
    cuenta_id                 BIGINT     NOT NULL,
    cliente_sesion_id         BIGINT     NOT NULL,  -- la sesión que lo mandó (RF-03)
    uuid_cliente              CHAR(36)   NOT NULL,  -- idempotencia (RNF-09)
    estado                    ENUM('PENDIENTE_APROBACION','APROBADO','RECHAZADO','CANCELADO',
                                   'EN_PREPARACION','LISTO','SERVIDO') NOT NULL DEFAULT 'PENDIENTE_APROBACION',
    motivo_rechazo            ENUM('NO_DISPONIBLE','PEDIDO_INVALIDO','MESA_NO_OCUPADA') NULL,  -- obligatorio si RECHAZADO (Service)
    prioridad_manual          BOOLEAN    NOT NULL DEFAULT FALSE,  -- RF-19, la pone Caja
    resuelto_por_usuario_id   BIGINT     NULL,  -- Caja que aprobó o rechazó
    servido_por_usuario_id    BIGINT     NULL,  -- Mozo
    cancelado_por_usuario_id  BIGINT     NULL,  -- NULL con cancelado_en lleno = lo canceló el cliente
    enviado_en                TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resuelto_en               TIMESTAMP  NULL,
    en_preparacion_en         TIMESTAMP  NULL,
    listo_en                  TIMESTAMP  NULL,
    servido_en                TIMESTAMP  NULL,
    cancelado_en              TIMESTAMP  NULL,
    version                   INT        NOT NULL DEFAULT 0,  -- optimistic locking (@Version), RNF-01
    PRIMARY KEY (id),
    CONSTRAINT uq_pedido_uuid_cliente UNIQUE (uuid_cliente),
    INDEX idx_pedido_cuenta_estado (cuenta_id, estado),
    INDEX idx_pedido_cola (local_id, estado, prioridad_manual, enviado_en),  -- cola del KDS (RF-22)
    CONSTRAINT fk_pedido_local          FOREIGN KEY (local_id)                 REFERENCES local (id),
    CONSTRAINT fk_pedido_cuenta         FOREIGN KEY (cuenta_id)                REFERENCES cuenta (id),
    CONSTRAINT fk_pedido_cliente_sesion FOREIGN KEY (cliente_sesion_id)        REFERENCES cliente_sesion (id),
    CONSTRAINT fk_pedido_resuelto_por   FOREIGN KEY (resuelto_por_usuario_id)  REFERENCES usuario (id),
    CONSTRAINT fk_pedido_servido_por    FOREIGN KEY (servido_por_usuario_id)   REFERENCES usuario (id),
    CONSTRAINT fk_pedido_cancelado_por  FOREIGN KEY (cancelado_por_usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE item_pedido (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    pedido_id        BIGINT         NOT NULL,
    plato_id         BIGINT         NOT NULL,
    cantidad         SMALLINT       NOT NULL,
    precio_unitario  DECIMAL(10,2)  NOT NULL,  -- copiado de plato.precio al pedir
    observacion      VARCHAR(200)   NULL,      -- ej. "sin cebolla"
    PRIMARY KEY (id),
    INDEX idx_item_pedido_pedido (pedido_id),
    CONSTRAINT fk_item_pedido_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_pedido_plato  FOREIGN KEY (plato_id)  REFERENCES plato (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ticket (
    id          BIGINT         NOT NULL AUTO_INCREMENT,  -- el número de ticket visible
    local_id    BIGINT         NOT NULL,
    cuenta_id   BIGINT         NOT NULL,
    emitido_en  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total       DECIMAL(10,2)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_ticket_cuenta UNIQUE (cuenta_id),  -- un ticket por cuenta
    CONSTRAINT fk_ticket_local  FOREIGN KEY (local_id)  REFERENCES local (id),
    CONSTRAINT fk_ticket_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuenta (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------
-- Avisos y auditoría
-- ---------------------------------------------------------------------

CREATE TABLE aviso (
    id                       BIGINT     NOT NULL AUTO_INCREMENT,
    local_id                 BIGINT     NOT NULL,
    mesa_id                  BIGINT     NOT NULL,
    tipo                     ENUM('LLAMAR_MOZO','CUENTA_SOLICITADA','MESA_SIN_MOZO') NOT NULL,
    creado_en                TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atendido_por_usuario_id  BIGINT     NULL,
    atendido_en              TIMESTAMP  NULL,
    PRIMARY KEY (id),
    INDEX idx_aviso_mesa_atendido (mesa_id, atendido_en),
    CONSTRAINT fk_aviso_local        FOREIGN KEY (local_id)                REFERENCES local (id),
    CONSTRAINT fk_aviso_mesa         FOREIGN KEY (mesa_id)                 REFERENCES mesa (id),
    CONSTRAINT fk_aviso_atendido_por FOREIGN KEY (atendido_por_usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- RF-36. Se guarda 6 meses (RNF-13): lo viejo lo borra un job, no una constraint.
-- Lo que hace el cliente anónimo no se audita.
CREATE TABLE registro_auditoria (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    local_id        BIGINT       NOT NULL,
    usuario_id      BIGINT       NOT NULL,
    accion          VARCHAR(60)  NOT NULL,  -- ej. APROBAR_PEDIDO, CERRAR_CUENTA
    entidad         VARCHAR(60)  NOT NULL,  -- ej. PEDIDO, CUENTA, PLATO
    entidad_id      BIGINT       NULL,
    valor_anterior  JSON         NULL,
    valor_nuevo     JSON         NULL,
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_auditoria_local_fecha (local_id, creado_en),
    INDEX idx_auditoria_entidad (entidad, entidad_id),
    CONSTRAINT fk_auditoria_local   FOREIGN KEY (local_id)   REFERENCES local (id),
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
