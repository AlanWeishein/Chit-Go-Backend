# chit-go-back

Backend de Chit&Go. Java 21, Spring Boot 4 (Spring Framework 7), JPA, MySQL 8.0.16+, WebSocket (STOMP).
Arquitectura hexagonal sobre un monolito modular.

## Estructura

```
src/main/java/com/chitgo/
├── compartido/        dominio compartido + infraestructura (web, seguridad, websocket, persistencia)
├── usuarios/
├── menu/
├── pedidos/
└── auditoria/
```

Cada módulo: `dominio/puertos`, `aplicacion/dto`, `infraestructura/{web,persistencia,websocket}`.
El dominio no depende de Spring, JPA ni WebSocket. Los controladores no tienen lógica de negocio.

## Cómo correrlo

1. Levantar MySQL con Docker: `docker compose up -d` (MySQL 8.4 en el puerto 3307, para no chocar con un MySQL instalado).
   Esperar a que `docker compose ps` lo muestre `healthy`. Si no hay `.env`, usa los valores
   por defecto de `.env.example`. Variables del back: `DB_URL`, `DB_USER`, `DB_PASSWORD`
   (por defecto `jdbc:mysql://localhost:3307/chitgo`, usuario y clave `chitgo`) y `JWT_CLAVE`, la
   clave para firmar la sesión (sin valor por defecto; si falta, el back usa una al azar y lo avisa
   en el log, ver `.env.example`).
   Para bajarlo: `docker compose down` (con `-v` borra también los datos).
2. `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
3. Probar: `GET http://localhost:8080/api/v1/ping`

## Datos de prueba (dev)

Con el perfil `dev`, Flyway además de las migraciones de `db/migration` corre
`db/dev/R__datos_de_prueba.sql`. Carga dos locales: **La Esquina**, el de los casos de prueba, y
**El Puerto**, para probar que los datos de un local no se mezclan con los del otro. El perfil por
defecto (`application.yml`) no incluye `db/dev`, así que esto nunca corre en producción.

> **Ojo:** la seed **borra todos los datos de negocio** (todo menos `rol`, `permiso` y `rol_permiso`),
> vuelve los ids a 1 y carga todo de nuevo. Como es una migración repetible (`R__`), Flyway la vuelve
> a aplicar cada vez que cambia el archivo. Cuando eso pasa, se pierden las cuentas y los pedidos que
> hayas probado a mano.

Contraseña de todos los que entran con contraseña: `Chitgo2026!`. Nadie tiene que cambiar la
credencial al entrar.

| Local | Rol | Nombre | Entra con |
|---|---|---|---|
| La Esquina | Gerente/Dueño | Dueño La Esquina | `dueno@laesquina.test` + contraseña |
| La Esquina | Admin | Admin La Esquina | `admin@laesquina.test` + contraseña |
| La Esquina | Mozo | Juan | PIN `1234`, o usuario `juan` + contraseña (mesas 1, 2 y 3) |
| La Esquina | Mozo | Ana | PIN `5678`, o usuario `ana` + contraseña (mesas 3 y 4) |
| La Esquina | Caja | Carla | PIN `2468` |
| La Esquina | Cocina | Hugo | PIN `1357` |
| El Puerto | Gerente/Dueño | Dueño El Puerto | `dueno@elpuerto.test` + contraseña |
| El Puerto | Mozo | Juan | PIN `4321`, o usuario `juan` + contraseña (mesas 1 y 2) |

Las mesas 5 y 6 de La Esquina no tienen mozo, para los casos de `MESA_SIN_MOZO`. Los mozos tienen
turno todos los días de 00:00 a 23:59. No se cargan cuentas, pedidos, avisos, auditoría ni
ausencias: los crea cada caso de prueba. Los tokens de los QR (mesas y staff) están en el
encabezado del SQL.

## Sesión y CSRF (para el front)

- La sesión del personal es un JWT en la cookie `chitgo_sesion` (HttpOnly, SameSite=Strict). El front
  no la puede leer: para saber quién está logueado llama a `GET /api/v1/auth/sesion`, que da 401 si no hay
  sesión.
- Públicas: `/api/v1/ping`, `/api/v1/public/**`, los `POST` de login (`/auth/pin`, `/auth/mozo/login`,
  `/auth/admin/login`) y `GET /auth/dispositivo`. Todo lo demás pide la cookie, y además que la credencial
  no sea provisoria (ver "Primer ingreso").
- CSRF: cualquier respuesta deja la cookie `XSRF-TOKEN`, que sí se lee desde JavaScript. Todo `POST`,
  `PUT`, `PATCH` y `DELETE` tiene que mandar su valor en el header `X-XSRF-TOKEN`, también los logins.
  Sin el header, o con otro valor, responde 403. Va en un solo lugar: `compartido/api`. La cookie cambia
  en cada respuesta a un request con sesión (también `/auth/sesion`): hay que leerla al mandar cada
  request, no guardarla en una variable.
- Los errores salen en ProblemDetail (`application/problem+json`).
- Con el perfil `dev` las cookies van sin `Secure`, para probar por http desde el celular con la IP de
  la compu. Fuera de dev, `COOKIE_SEGURA=false` hace lo mismo.

### Login con PIN (Mozo, Caja y Cocina)

- `GET /api/v1/public/staff/{tokenStaff}` devuelve `{nombreLocal, usuarios: [{id, nombre}]}`: los activos
  del local que entran con PIN, ordenados por nombre. Un token que no existe da 404 con
  "El QR de staff no es válido.", sin datos de ningún local.
- `POST /api/v1/auth/pin` con `{tokenStaff, usuarioId, pin}` (PIN de 4 a 10 dígitos). Si entra, deja la
  cookie de sesión y responde 200 con lo mismo que `/auth/sesion`. Si no:
  - 401 "PIN incorrecto": suma un intento fallido.
  - 401 con `"codigo": "CONTRASENA_REQUERIDA"`: es un mozo y este dispositivo no tiene su cookie de
    dispositivo vigente. Hay que mostrar P-32 (ver abajo). No mira el PIN ni suma intentos. Comparar el
    `codigo`, no el texto del `detail`.
  - 423 "Tu usuario está bloqueado. Pedile al Admin que te blanquee el PIN.": al quinto intento fallido
    seguido, y desde ahí también con el PIN correcto. No vence solo. Se mira antes que la cookie de
    dispositivo: un mozo bloqueado ve el 423 aunque no tenga la cookie.
  - 404: el token de staff no existe, o el usuario no es de ese local, está dado de baja o no entra con
    PIN. No suma intentos.
  - 400: falta un dato o el PIN no tiene de 4 a 10 dígitos. No suma intentos.

### Usuario y contraseña del mozo, cada 7 días (P-32)

- Al abrir la app del mozo, `GET /api/v1/auth/dispositivo`. Responde 200 con `{id, nombre, venceEn}` si
  en este navegador hay una cookie de dispositivo vigente (el front puede ir directo al PIN de ese mozo), o
  204 si no la hay, venció o el mozo fue dado de baja: entonces va P-32. Es un `GET`, no lleva el header
  CSRF, y no renueva nada.
- `POST /api/v1/auth/mozo/login` con `{tokenStaff, nombreUsuario, password}`. El usuario es único dentro
  del local del QR y no distingue mayúsculas. Si entra, responde 200 con `{id, nombre, venceEn}` y deja la
  cookie `chitgo_dispositivo` (HttpOnly, SameSite=Strict, 7 días, `Path=/api/v1/auth`: viaja a todo lo que
  cuelga de esa ruta). **No abre la sesión**: después va el PIN de ese mozo con `/auth/pin`. Si no:
  - 401 "Usuario o contraseña incorrectos", siempre el mismo: el usuario no existe en ese local, la
    contraseña está mal, o está bloqueado, dado de baja o no es mozo. La contraseña mal suma un intento, con
    el mismo contador que el PIN, y al quinto queda bloqueado.
  - 404 "El QR de staff no es válido.": el token de staff no existe.
  - 400: falta un dato, el usuario pasa de 40 caracteres, o la contraseña no tiene de 8 a 64 caracteres o
    pasa de 72 bytes. No suma intentos.
- Los 7 días se cuentan desde la contraseña: entrar con el PIN no los renueva, y el logout no borra la
  cookie de dispositivo. Hay una por navegador: si otro mozo entra con contraseña en el mismo celular, la
  pisa.

### Login con email (Admin y Gerente/Dueño)

- `POST /api/v1/auth/admin/login` con `{email, password}`. El email no distingue mayúsculas. Si entra,
  deja la cookie de sesión y responde 200 con lo mismo que `/auth/sesion`; el Gerente/Dueño viene con
  `local: null`. Si no:
  - 401 "Email o contraseña incorrectos", siempre el mismo: el email no existe, la contraseña está mal, o
    el usuario está bloqueado o dado de baja. Acá no hay 423. La contraseña mal suma un intento, y al
    quinto seguido queda bloqueado (lo destraba el Gerente/Dueño).
  - 400: falta un dato, o la contraseña no tiene de 8 a 64 caracteres o pasa de 72 bytes (con acentos o
    emojis cada carácter ocupa más de uno). No suma intentos.

### Primer ingreso (P-29, P-30, P-31 y P-32b)

- Si el login responde con `debeCambiarCredencial: true`, la credencial es provisoria (alta o blanqueo).
  Hasta que elija la suya, solo puede usar `POST /auth/credencial`, `GET /auth/sesion` y
  `POST /auth/logout`. Todo lo demás da 403 con `"codigo": "CREDENCIAL_PROVISORIA"` (comparar el `codigo`).
- El mozo entra como siempre (P-32 con la contraseña provisoria y después el PIN provisorio), y recién ahí
  elige: primero la contraseña (P-32b) y después el PIN (P-30). El front junta las dos y las manda juntas.
- `POST /api/v1/auth/credencial` con `{pin, pinRepetido, password, passwordRepetida}`:
  - Mozo, Caja y Cocina mandan el PIN (4 a 10 dígitos). El Admin y el Gerente/Dueño, la contraseña (8 a 64
    caracteres, hasta 72 bytes). El mozo además puede mandar la contraseña: en el primer ingreso hay que
    pedírsela; después de un blanqueo de PIN, no. Los campos que no van, en `null` o sin mandar.
  - Si sale bien, responde 200 con lo mismo que `/auth/sesion`, ya con `debeCambiarCredencial: false`, y
    deja una cookie de sesión nueva. La cookie de dispositivo del mozo no cambia.
  - 400 "No coinciden": la repetición no es igual. 400 "El PIN nuevo tiene que ser distinto del anterior."
    o "La contraseña nueva tiene que ser distinta de la anterior.". 400 "Elegí tu PIN." o "Elegí tu
    contraseña." si falta la que le toca, y 400 genérico si manda una que su rol no usa o no tiene el
    formato. En todos los casos no se guarda nada: si al mozo le falla la contraseña, tampoco se guarda el PIN.
  - 409 "Tu credencial no es provisoria.": ya la eligió.

### Duración y corte por inactividad (RNF-14)

- El token dura T minutos y cada request con sesión lo renueva: la respuesta trae una cookie
  `chitgo_sesion` nueva con T contado desde ahí. A los T minutos sin actividad vence solo y lo próximo da
  401 "No hay una sesión iniciada, o venció.": el front vuelve al login.
- T según el rol: Caja, 60 minutos (fijo); Mozo, Cocina y Admin, el del local
  (`minutos_inactividad_sesion`, 10 por defecto); el Gerente/Dueño, 10.
- No renuevan: las públicas, `GET /auth/sesion` (consultarla no cuenta como actividad), `POST
  /auth/credencial` (deja su propia cookie nueva) y `POST /auth/logout` (la borra). Un login con la cookie
  de otro usuario todavía puesta deja solo la del que entra.
- Cualquier request automático con sesión cuenta como actividad: un polling, el refetch al volver a la
  pestaña, un reintento. Si el front pide algo solo cada tanto, la sesión no se corta nunca. Las
  novedades van por el WebSocket.
- Aviso de Caja a los 55 minutos: el vencimiento se corre con cada request. El front puede reiniciar su
  cuenta en cada respuesta, o leer `expiraEn` de `GET /auth/sesion`, que no renueva.
- No cerrar la sesión ni cambiar de usuario con requests pendientes: una respuesta que llega después del
  logout vuelve a dejar la cookie del anterior.
- La renovación mira la base: un usuario dado de baja da 401 en el próximo request, y uno al que le
  blanquearon la credencial da 403 con `CREDENCIAL_PROVISORIA`.

## Fechas

La conexión a MySQL va en UTC (`connectionTimeZone` en `application.yml`), igual que los `Instant` de
Java. Al mirar la base a mano, las columnas `TIMESTAMP` se ven en la zona de tu sesión de MySQL.

## Ramas

`main` estable, `develop` en desarrollo, `feature/...` por tarea. PR a `develop`, lo revisa otro.

## Pendiente

- Permisos por endpoint contra `rol_permiso` (hoy alcanza con estar logueado).
