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
  `/auth/admin/login`) y `GET /auth/dispositivo`. Todo lo demás pide la cookie, que la credencial
  no sea provisoria (ver "Primer ingreso") y el permiso del endpoint (ver "Permisos y auditoría").
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

## Cola de aprobación (para el front)

HU-07, pantalla P-08. Las tres rutas piden sesión de Caja (el permiso `pedido.aprobar_rechazar`): sin sesión
dan 401 y con otro rol, 403. Los errores salen en ProblemDetail y el `detail` se puede mostrar tal cual.

- `GET /api/v1/caja/pedidos?estado=PENDIENTE_APROBACION`. `estado` se puede omitir; otro valor da 400.
  Devuelve la lista sin envolver, del pedido más viejo al más nuevo:

```json
[
  {
    "id": 12,
    "mesa": "1",
    "enviadoEn": "2026-10-21T23:00:00Z",
    "segundosDeEspera": 181,
    "demorado": true,
    "items": [
      {"platoId": 2, "plato": "Ensalada mixta", "cantidad": 1, "observacion": "sin sal", "disponible": false}
    ]
  }
]
```

  - `mesa` es el número que se muestra, no el id.
  - `segundosDeEspera` y `demorado` los calcula el back con su reloj, en el momento del `GET`. `demorado` es
    más de 3 minutos: a los 180 segundos todavía no, a los 181 sí. El aviso en vivo está en "Alerta de los
    3 minutos".
  - `disponible: false` quiere decir que el plato se apagó después de que el cliente lo pidió. No van precios.
- `POST /api/v1/caja/pedidos/{id}/aprobar`, sin body. Responde 204.
- `POST /api/v1/caja/pedidos/{id}/rechazar` con `{"motivo": "MESA_NO_OCUPADA"}`. Los motivos son
  `NO_DISPONIBLE`, `PEDIDO_INVALIDO` y `MESA_NO_OCUPADA`. Responde 204; sin motivo o con otro valor, 400.
  **No lleva `version`**: el back la toma de la base, y si llega se ignora.
- Los dos `POST` llevan el header `X-XSRF-TOKEN`, como todo `POST` (ver "Sesión y CSRF"). Sin él, 403.
- Al aprobar o rechazar:
  - 404 "El pedido no existe": no está, o es de otro local.
  - 409 "El pedido ya fue cancelado": lo canceló el cliente antes.
  - 409 "El pedido ya fue resuelto": lo resolvió otra caja, aunque haya sido en el mismo instante.
  - Con cualquiera de los dos 409, el pedido sale de la cola.
- Tiempo real, en `/topic/local/{localId}/caja/pedidos`: `PEDIDO_ENVIADO` con cada pedido nuevo,
  `PEDIDO_CANCELADO` cuando el cliente cancela y `PEDIDO_RESUELTO` cuando cualquier caja aprueba o rechaza.
  Todas las cajas sacan el pedido de la cola con `PEDIDO_RESUELTO`, también la que lo resolvió. Trae el
  `estado` (`APROBADO` o `RECHAZADO`) y no trae `detalle`. `mesaId` es el id de la mesa, no el número:

```json
{"tipo": "PEDIDO_RESUELTO", "pedidoId": 12, "mesaId": 1, "estado": "APROBADO", "detalle": null,
 "ocurridoEn": "2026-10-21T23:03:05Z"}
```

- Los eventos que se pierden con el WebSocket caído no se repiten: al reconectar, volver a pedir el `GET`.
- Lo prueba `AprobacionDePedidosTest`, contra el MySQL del Compose.

## Alerta de los 3 minutos (para el front)

HU-08, pantalla P-08. Cuando un pedido pasa los 3 minutos sin que Caja lo apruebe o lo rechace, el back publica
`ALERTA_DEMORA` en `/topic/local/{localId}/caja/pedidos`, el mismo topic de la cola. La regla es la del
`demorado` del `GET`: a los 180 segundos todavía no, a los 181 sí.

```json
{"tipo": "ALERTA_DEMORA", "pedidoId": 12, "mesaId": 1, "cuentaId": null, "estado": "PENDIENTE_APROBACION",
 "detalle": null, "ocurridoEn": "2026-10-21T23:03:05Z"}
```

- `mesaId` es el id de la mesa, como en los demás eventos. El número que se muestra está en el `mesa` del `GET`.
- `ocurridoEn` es la hora en que el back lo detectó, no la hora del envío del pedido: la espera sale del
  `enviadoEn` del `GET`.
- Llega **una sola vez por pedido**. Si el pedido se aprueba, se rechaza o se cancela antes, no llega.
- Lo publica una tarea que corre cada 5 segundos, así que puede llegar hasta unos 5 segundos después de los 181.
- **Se puede perder**: si el back se reinicia justo cuando el pedido cruza los 3 minutos, o si el WebSocket está
  caído en ese momento. Por eso, al conectar o reconectar, la verdad es el `demorado` del `GET`: con el aviso se
  marca el pedido que ya está en pantalla, y el `GET` trae los que ya estaban demorados.
- Un pedido que ya estaba demorado cuando arrancó el back no recibe aviso: sale `demorado` en el `GET`.
- Cada local recibe solo los avisos de sus pedidos.
- La tarea se prende y se apaga con `chitgo.alertas.demora.habilitada` (`true` en `application.yml`). Los tests la
  apagan en `src/test/resources/config/application.yml` y llaman a la revisión a mano con el reloj de prueba.
- Lo prueban `AlertaDeDemoraTest` (CP-20-01 a CP-20-04, contra el MySQL del Compose) y
  `AlertaDeDemoraProgramadaTest` (que la tarea quede agendada cada 5 segundos).

## Pedidos aprobados (para el front)

ST-17, pantalla P-36. Caja ve los pedidos aprobados que Cocina todavía no terminó. Pide el permiso
`pedido.priorizar`, que tiene solo Caja: sin sesión da 401, y con Cocina, Mozo o Admin, 403. Un usuario sin local
fijo (el Gerente/Dueño) también da 403.

- `GET /api/v1/caja/pedidos/aprobados`. Responde 200 con dos listas, que pueden venir vacías:

```json
{
  "sinEmpezar": [
    {"id": 12, "mesa": "1", "estado": "APROBADO", "prioritario": true, "aprobadoEn": "2026-10-21T23:01:00Z",
     "enPreparacionEn": null, "listoEn": null,
     "items": [{"plato": "Milanesa napolitana", "cantidad": 2, "observacion": "sin cebolla"}]}
  ],
  "enPreparacion": []
}
```

  - Cada pedido es el mismo `PedidoDeCocinaDto` de `GET /cocina/pedidos`. `mesa` es el número que se muestra.
  - `sinEmpezar` son los `APROBADO`, en el orden de la cola de cocina: los prioritarios primero y después por
    hora de aprobación (`aprobadoEn`). El front no los reordena.
  - `enPreparacion` son los `EN_PREPARACION`, del que empezó antes al que empezó después.
  - No van los pendientes, los listos, los servidos, los rechazados ni los cancelados.
- Es solo lectura: no se audita ni publica nada. Priorizar es `PUT /caja/pedidos/{id}/prioridad` y cancelar,
  `POST /caja/pedidos/{id}/cancelar`.
- Tiempo real, en `/topic/local/{localId}/caja/pedidos`: además de `PRIORIDAD_CAMBIADA` y `PEDIDO_CANCELADO`,
  llegan `EN_PREPARACION` cuando Cocina empieza un pedido y `LISTO` cuando lo termina, con el `pedidoId`, el
  `mesaId` y el `estado` nuevo. Con cualquiera de los eventos, el front vuelve a pedir el `GET`.
  La cola de aprobación (P-08) y las cuentas (P-09) escuchan el mismo topic y también van a recargar.
- Lo prueban `PedidosAprobadosParaCajaTest` (contra el MySQL del Compose) y `ColaDeCocinaServiceTest` (el aviso).

## Cancelar un pedido

Para quien arma un Service que cancela (la cancelación en bloque de HU-09, el cierre de cuenta de HU-10).
Hay una sola forma, en el dominio: `pedido.cancelar(origen, usuarioId, ahora)`. Cada `OrigenDeCancelacion`
dice desde qué estados puede cancelar:

| Origen | Pendiente | Aprobado | En preparación | Listo | Servido, Rechazado, Cancelado |
|---|---|---|---|---|---|
| `CLIENTE` | sí | no | no | no | no |
| `CAJA` | sí | sí | no | no | no |
| `CIERRE_DE_CUENTA` | sí | sí | sí | sí | no |

- Guarda `CANCELADO`, `canceladoEn` y `canceladoPorUsuarioId`. Con `CLIENTE` el usuario va en `null` (así se
  sabe que canceló el cliente); con `CAJA` y `CIERRE_DE_CUENTA` es obligatorio. Pasarlo mal es un error de
  programación y da `IllegalArgumentException`, no un 4xx.
- Desde un estado que el origen no acepta, `ReglaDeNegocioException` (409), y el pedido no cambia. Con
  `CLIENTE` el `detail` es el de HU-02 (por ejemplo "El pedido ya fue aprobado. Si necesitás cambiarlo, llamá
  al mozo."). Con `CAJA` y `CIERRE_DE_CUENTA`: "El pedido ya fue cancelado", "El pedido ya se está
  preparando y no se puede cancelar" (solo `CAJA`) o "El pedido ya no se puede cancelar".
- El origen no se guarda en `pedido`: va en la auditoría que haga el Service.
- `cancelar` no guarda, no audita ni publica: eso lo hace el Service, dentro de su transacción, como
  `CancelarPedidoService` (guardar y pasar el choque del `@Version` a 409) y `ResolverPedidoService`
  (auditar y publicar).
- `cancelarPorCliente(ahora)` sigue estando y es `cancelar(CLIENTE, null, ahora)`.

## Cierre de cuenta (para el front)

HU-10, pantalla P-09. Pide sesión de Caja (el permiso `cuenta.cerrar`): sin sesión da 401, con otro rol 403, y el
Gerente/Dueño, que no tiene local, también 403. Son tres rutas: el listado de cuentas por cobrar, el cierre y la
reimpresión del ticket.

### Cuentas por cobrar

- `GET /api/v1/caja/cuentas?estado=ABIERTA,SOLICITADA`: las cuentas sin cerrar del local de la sesión. Primero
  las Solicitadas, por `solicitadaEn`, y después las Abiertas, por `abiertaEn`.
- `estado` acepta `ABIERTA`, `SOLICITADA` o los dos separados por coma, en mayúsculas. Sin `estado` son los dos.
  `CERRADA` o cualquier otro valor da 400 "Las cuentas por cobrar se piden con estado ABIERTA, SOLICITADA o los
  dos separados por coma".

```json
[
  {"id": 7, "mesa": "2", "estado": "SOLICITADA", "abiertaEn": "2026-10-30T20:40:00Z",
   "solicitadaEn": "2026-10-30T21:10:00Z", "total": 6000.00, "pedidosSinServir": 1},
  {"id": 6, "mesa": "1", "estado": "ABIERTA", "abiertaEn": "2026-10-30T20:30:00Z",
   "solicitadaEn": null, "total": 17500.00, "pedidosSinServir": 1}
]
```

- `mesa` es el número que se muestra. `total` suma todos los pedidos que no están Rechazados ni Cancelados,
  **también los sin servir**. No es lo que se va a cobrar si Caja cierra en ese momento: el cierre cancela lo sin
  servir y el ticket sale por menos. `pedidosSinServir` dice cuántos hay, para avisarlo.

### Cerrar la cuenta

- `POST /api/v1/caja/cuentas/{id}/cerrar`, con el header `X-XSRF-TOKEN`. El body es opcional; sin body es
  `{"confirmar": false}`:

```json
{"confirmar": true, "pedidosACancelar": [41]}
```

- Si la cuenta es de otro local o no existe, 404 "La cuenta no existe". Si ya está cerrada, 409 "La cuenta ya
  está cerrada".
- Si no hay nada que confirmar, cierra y responde 200 con el ticket. Hay que confirmar cuando la cuenta está
  Abierta (nadie la pidió) o tiene pedidos sin servir (Pendiente, Aprobado, En preparación o Listo). Si hay
  que confirmar y no llega `confirmar: true`, 409 con el campo `codigo`:

```json
{
  "status": 409,
  "detail": "Hay pedidos sin servir. ¿Estás seguro de que querés cerrar la cuenta?",
  "codigo": "CONFIRMAR_CIERRE",
  "motivos": ["PEDIDOS_SIN_SERVIR"],
  "pedidosSinServir": [
    {"id": 41, "estado": "EN_PREPARACION", "platos": [{"plato": "Milanesa napolitana", "cantidad": 2}]}
  ]
}
```

  - `motivos` puede traer `NADIE_PIDIO_LA_CUENTA`, `PEDIDOS_SIN_SERVIR` o los dos, en ese orden. Con los dos,
    `detail` trae los dos mensajes seguidos: conviene pintar desde `motivos`. `pedidosSinServir` es `[]` si el
    único motivo es que nadie pidió la cuenta.
  - Al confirmar, el front manda `confirmar: true` y los ids de `pedidosSinServir` en `pedidosACancelar`: lo que
    Caja vio. Si al cerrar aparece un pedido sin servir que no está en esa lista (entró otro pedido), no se
    cierra nada y vuelve el mismo 409 con la lista de ahora. Lo que se sirvió o se canceló en el medio no frena.
  - Al cerrar se cancelan todos los pedidos que estén sin servir en ese momento (salen del KDS y no suman al
    total), con Caja como quien los canceló.
- Un choque con otra operación en el mismo instante (Cocina marca Listo un pedido, el cliente manda otro) da 409
  con el `detail` de siempre y no guarda nada: Caja reintenta.
- La respuesta es el ticket, en JSON; lo imprime el front. `numero` es el id del ticket, `mesa` el número que se
  muestra. El mismo plato al mismo precio va en un solo renglón. Solo cuentan los pedidos que no quedaron
  Rechazados ni Cancelados; con todo cancelado el total es 0 y el ticket sale igual:

```json
{
  "numero": 5, "local": "La Esquina", "mesa": "1", "emitidoEn": "2026-10-30T21:15:00Z",
  "items": [
    {"plato": "Milanesa napolitana", "cantidad": 2, "precioUnitario": 9500.00, "subtotal": 19000.00},
    {"plato": "Flan casero", "cantidad": 1, "precioUnitario": 4000.00, "subtotal": 4000.00}
  ],
  "total": 23000.00, "letra": "X", "leyenda": "DOCUMENTO NO VÁLIDO COMO FACTURA"
}
```

- Tiempo real: por cada pedido cancelado, `PEDIDO_CANCELADO` a cocina (`cocina/cola`), a caja (`caja/pedidos`) y a
  la pantalla, y `CANCELADO` a su cliente. Para la cuenta, `CUENTA_CERRADA` a caja (`caja/pedidos`) y a los
  mozos (`mozo/avisos`): las otras cajas la sacan de cuentas por cobrar y la mesa queda libre. Es el primer
  evento de la cuenta que trae `cuentaId`:

```json
{"tipo": "CUENTA_CERRADA", "pedidoId": null, "mesaId": 1, "cuentaId": 7, "estado": "CERRADA", "detalle": null,
 "ocurridoEn": "2026-10-30T21:15:00Z"}
```

  Todos los eventos ahora llevan `cuentaId`; en los de un pedido va en `null`.
- Mandar un pedido sube siempre la versión de la cuenta: si el cliente manda un pedido justo mientras Caja cierra,
  uno de los dos recibe 409 y reintenta. Quien manda el pedido recibe "La cuenta cambió justo antes. Probá de
  nuevo." o, si el cierre ya terminó, "La cuenta de esta mesa ya se cerró".
- Queda en la auditoría un solo registro, `CERRAR_CUENTA` sobre la cuenta, con el total, el id del ticket y los
  ids de los pedidos cancelados.
- Después del cierre la mesa queda libre: si el celular que abrió la cuenta manda otro pedido, recibe 409 "La
  cuenta de esta mesa ya se cerró", y el próximo que escanea el QR abre una cuenta nueva como iniciador.

### Reimprimir el ticket

- `GET /api/v1/caja/cuentas/{id}/ticket`: el mismo JSON que devolvió el cierre, con el mismo `numero`,
  `emitidoEn` y `total`.
- Si la cuenta todavía no está cerrada, 404 "Esta cuenta todavía no tiene ticket". Si es de otro local o no
  existe, 404 "La cuenta no existe".
- Los renglones se arman de nuevo desde los pedidos, con el precio de cada pedido. Si el nombre de un plato cambió
  después del cierre, sale con el nombre nuevo.

### Cómo se prueba

`CierreDeCuentaTest` recorre HU-10 contra el MySQL del Compose (CP-22-01 a CP-22-07, dos cierres a la vez, otro
local y el CSRF). Dos pasos van por SQL porque el back todavía no los tiene: servir un pedido (HU-05) y pedir la
cuenta (el cliente).

## Carta del Admin (para el front)

HU-12, pantalla P-14. Todas las rutas piden sesión de Admin (el permiso `menu.gestionar`): sin sesión dan 401 y
con otro rol, 403 "No tenés permiso para hacer esto.". Los `POST`, `PUT`, `PATCH` y `DELETE` llevan el header
`X-XSRF-TOKEN` (ver "Sesión y CSRF"); sin él, 403. Los errores salen en ProblemDetail y el `detail` se puede
mostrar tal cual. Un plato, una categoría o una etiqueta de otro local da el mismo 404 que uno que no existe,
también cuando viene en el body.

Los cambios no se avisan en vivo: el cliente los ve la próxima vez que carga la carta.

### Platos

- `GET /api/v1/admin/platos`: todos los platos del local, **también los fuera de carta** (`disponible: false`).
  Vienen ordenados por el orden de la categoría y después por nombre, con las etiquetas por nombre:

```json
[
  {"id": 2, "nombre": "Ensalada mixta", "descripcion": null, "precio": 6000.00,
   "categoria": {"id": 1, "nombre": "Principales"}, "imagenUrl": "/img/ensalada.jpg",
   "destacado": false, "disponible": true,
   "etiquetas": [{"id": 1, "nombre": "Sin TACC"}, {"id": 2, "nombre": "Vegano"}]}
]
```

- `POST /api/v1/admin/platos`: el alta. Nace disponible y sin destacar. Responde 201 con el plato, como en el `GET`:

```json
{"nombre": "Empanada de carne", "descripcion": "Cortada a cuchillo", "precio": 1800, "categoriaId": 3,
 "imagenUrl": "/img/empanada.jpg", "etiquetaIds": [1]}
```

- `PUT /api/v1/admin/platos/{id}`: la edición, con el mismo body. Responde 200 con el plato. `destacado` y
  `disponible` no se cambian acá.
  - **Se manda el plato entero.** Las etiquetas se reemplazan por las de `etiquetaIds`: si el campo no viene o
    viene `null`, el plato se queda sin etiquetas.
- `PATCH /api/v1/admin/platos/{id}/destacado` con `{"destacado": true}`. Responde 204. Los destacados salen
  arriba en la carta del cliente.
- **No hay `DELETE` de platos.** Sacar un plato de la carta es lo mismo que dejarlo no disponible:
  `PATCH /api/v1/platos/{id}/disponibilidad` (HU-09, ver "Disponibilidad"). El plato sigue en este `GET`, con
  `disponible: false`.
- Errores del alta y la edición:
  - 400 "Poné un nombre": sin nombre o en blanco.
  - 400 "El precio tiene que ser mayor a cero": sin precio, cero o negativo.
  - 400 "La imagen tiene que ser un link https o una ruta del front" (ver abajo).
  - 400 "Los datos enviados no son válidos.": nombre de más de 120 caracteres, descripción de más de 500,
    precio con más de dos decimales o sin `categoriaId`.
  - 404 "El plato no existe", "La categoría no existe" o "La etiqueta no existe".
- **La imagen** es un link `https://...` o una ruta del propio front que empieza con `/` (por ejemplo
  `/img/milanesa.jpg`, en la carpeta `public` del front), de hasta 255 caracteres. `http://` no va, porque el
  navegador la bloquea en una página `https`, y tampoco una ruta que empieza con `//`, que lleva a otro sitio.
  Vacía o `null` queda sin imagen. No se suben archivos.
- El precio vuelve siempre con dos decimales (`1800` queda `1800.00`). Cambiarlo no toca los pedidos que ya
  se mandaron: cada uno guarda el precio del momento.

### Categorías

- `GET /api/v1/admin/categorias`: las del local **en su orden**, con cuántos platos tiene cada una y cuántos
  de esos están fuera de carta. La posición en la lista es el orden; no viene un campo `orden`:

```json
[
  {"id": 1, "nombre": "Principales", "platos": 2, "fueraDeCarta": 0},
  {"id": 2, "nombre": "Postres", "platos": 1, "fueraDeCarta": 1}
]
```

- `POST /api/v1/admin/categorias` con `{"nombre": "Entradas"}`: va al final del orden. Responde 201.
- `PUT /api/v1/admin/categorias/{id}` con `{"nombre": "..."}`: renombrar. Responde 200.
- `PUT /api/v1/admin/categorias/orden` con `{"ids": [2, 1, 3]}`: el orden nuevo, **con todas las categorías del
  local** y cada una una sola vez. Al arrastrar en P-14 se manda la lista entera. Responde 204. Si falta una,
  sobra una o se repite, 400 "El orden tiene que traer todas las categorías del local, una vez cada una".
- `DELETE /api/v1/admin/categorias/{id}`: solo si no tiene platos, contando los fuera de carta. Responde 204.
  Con platos, 409 "La categoría tiene 3 platos (1 fuera de carta). Movelos a otra antes de borrarla".
- Sin nombre, 400 "Poné un nombre"; de más de 80 caracteres, el 400 genérico.

### Etiquetas

- `GET /api/v1/admin/etiquetas`: las del local, por nombre, con `id` y `nombre`.
- `POST /api/v1/admin/etiquetas` con `{"nombre": "Picante"}` (201) y `PUT /api/v1/admin/etiquetas/{id}` (200).
- Repetida, al crear o al renombrar: 409 "Esa etiqueta ya existe". No distingue mayúsculas ni tildes: "Sin TACC",
  "sin tacc" y "Sín TACC" son la misma.
- `DELETE /api/v1/admin/etiquetas/{id}`: responde 204 y la etiqueta se va también de los platos que la tenían.
- Sin nombre, 400 "Poné un nombre"; de más de 60 caracteres, el 400 genérico.

### Auditoría y cómo se prueba

- Cada cambio deja un registro: `CREAR_PLATO`, `EDITAR_PLATO`, `DESTACAR_PLATO`, `CREAR_CATEGORIA`,
  `EDITAR_CATEGORIA`, `ORDENAR_CATEGORIAS`, `BORRAR_CATEGORIA`, `CREAR_ETIQUETA`, `EDITAR_ETIQUETA` y
  `BORRAR_ETIQUETA`. `EDITAR_PLATO` lleva solo los campos que cambiaron, así que el historial de precio sale de
  ahí. Un cambio que no cambia nada no audita.
- `CartaDelAdminTest` recorre HU-12 contra el MySQL del Compose (CP-25-01 a CP-25-08, menos CP-25-06, que es de
  la ST-12), con el login del Admin de verdad y la carta del cliente por el QR de la mesa 1.

## Empleados (para el front)

HU-14, pantallas P-16 y P-23. Todas las rutas piden sesión de Admin (el permiso `empleado.gestionar`): sin sesión
dan 401 y con otro rol, 403 "No tenés permiso para hacer esto." (Juan, el Mozo, y el Gerente/Dueño también).
Los `POST`, `PUT`, `PATCH` y `DELETE` llevan el header `X-XSRF-TOKEN` (ver "Sesión y CSRF"); sin él, 403. Los
errores salen en ProblemDetail y el `detail` se puede mostrar tal cual. El local sale de la sesión. Un empleado,
una mesa o una ausencia de otro local da el mismo 404 que uno que no existe, también cuando viene en el body.

Solo se gestionan Mozo, Caja y Cocina. **El Admin no aparece en la lista** y su id da 404 "El empleado no
existe": nadie se da de baja a sí mismo. Al Admin lo crea el Gerente/Dueño.

### Listado y detalle

- `GET /api/v1/admin/empleados?page=0&size=20`: los empleados del local, por nombre. **También los dados de
  baja**, con `activo: false`. Responde la página:

```json
{"contenido": [
   {"id": 4, "nombre": "Juan Pérez", "rol": "MOZO", "nombreUsuario": "juan", "activo": true,
    "bloqueado": false, "ausenciaVigente": false,
    "turnos": [{"dia": "LUN", "horaInicio": "12:00:00", "horaFin": "16:00:00"}]}
 ],
 "page": 0, "size": 20, "totalElementos": 4, "totalPaginas": 1}
```

  - **Esta forma es provisoria**: la propuso Claude Code porque en `develop` no había ningún listado paginado, y
    falta acordarla con German, que pagina auditoría, reportes y mesas. Si cambia, cambia acá.
  - `page` empieza en 0. `size` vale 20 si no viene y el máximo es 100. Fuera de rango, 400 "La página o el
    tamaño no son válidos". Los empleados con el mismo nombre se ordenan por id, así las páginas no repiten ni
    salteen a nadie.
  - `rol` es el código (`MOZO`, `CAJA` o `COCINA`). `nombreUsuario` va en `null` si no es mozo. `bloqueado` es
    que llegó a los 5 intentos fallidos (se destraba con el blanqueo de PIN). `ausenciaVigente` es que hoy cae
    dentro de una ausencia.
  - `dia` es `LUN`, `MAR`, `MIE`, `JUE`, `VIE`, `SAB` o `DOM`. Las horas se mandan como `"12:00"` y **vuelven con segundos**, `"12:00:00"` (medido por HTTP).
    Un `page` que no es un número también da 400.
- `GET /api/v1/admin/empleados/{id}`: el detalle. Es lo que responden también los `PUT` y el blanqueo:

```json
{"id": 4, "nombre": "Juan Pérez", "rol": "MOZO", "nombreUsuario": "juan", "activo": true, "bloqueado": false,
 "ausenciaVigente": false, "debeCambiarCredencial": false, "contrasenaProvisoria": false,
 "turnos": [{"dia": "LUN", "horaInicio": "12:00:00", "horaFin": "16:00:00"}],
 "mesaIds": [1, 2, 3],
 "ausencias": [{"id": 7, "tipo": "VACACIONES", "desde": "2026-12-01", "hasta": "2026-12-15"}]}
```

  - Las mesas vienen como `mesaIds`, sin el número. Las ausencias, de la más nueva a la más vieja. Nunca viene un
    PIN, una contraseña ni su hash.

### Alta, edición y baja

- `POST /api/v1/admin/empleados`: el alta. Responde 201 con el detalle.

```json
{"nombre": "Pedro Gómez", "rol": "MOZO", "pin": "0000", "nombreUsuario": "pedro", "password": "Provisoria2026"}
```

  - `nombre`, `rol` y `pin` van siempre. `nombreUsuario` y `password` van **solo si es Mozo** y son obligatorios;
    con Caja o Cocina, 400 "Solo el mozo lleva usuario y contraseña".
  - El PIN y la contraseña los escribe el Admin y son provisorios: el empleado nace con
    `debeCambiarCredencial: true` y tiene que elegir los suyos (ver "Primer ingreso"). Al mozo también le queda
    `contrasenaProvisoria: true`.
  - El `rol` se acepta en mayúsculas o minúsculas y los textos se recortan.
  - El nuevo sale en la lista del login de su local.
- `PUT /api/v1/admin/empleados/{id}`: la edición. Responde 200 con el detalle.

```json
{"nombre": "Pedro Gómez", "nombreUsuario": "pedro"}
```

  - **Se manda el empleado entero:** `nombre` y, si es Mozo, `nombreUsuario`. Caja y Cocina no mandan
    `nombreUsuario` (400 "Solo el mozo lleva usuario").
  - **El rol, el PIN y la contraseña no se cambian acá.** Para cambiar de rol, baja y alta. Para el PIN, el
    blanqueo (ver abajo).
  - Una edición sin cambios responde 200 y no hace nada.
- `DELETE /api/v1/admin/empleados/{id}`: baja lógica. Responde 204. No borra nada: sus pedidos, turnos y
  auditoría quedan. Sale de la lista del login y no puede entrar. Darlo de baja dos veces responde lo mismo.
- **Un dado de baja es de solo lectura.** La edición, los turnos, las mesas, las ausencias nuevas y el blanqueo de
  PIN responden 409 "El empleado está dado de baja". Se puede seguir pidiendo su detalle y sus ausencias.
- **Su usuario queda ocupado:** si "juan" se va, no se puede dar de alta otro "juan" en ese local.
- Errores del alta y la edición:
  - 400 "Falta el nombre", "Falta el rol", "El rol tiene que ser Mozo, Caja o Cocina", "Falta el PIN", "Falta el
    usuario del mozo" y "Falta la contraseña del mozo". **El campo viene en el texto del `detail`**, no en un
    campo aparte.
  - 400 "Los datos enviados no son válidos.": el PIN no tiene de 4 a 10 dígitos, la contraseña no tiene de 8 a
    64 caracteres o pasa de 72 bytes, el nombre pasa de 120 caracteres o el usuario de 40.
  - 409 "Ya existe un usuario con ese nombre": el usuario del mozo ya está en el local. No distingue mayúsculas
    y tampoco tildes ("juan", "JUAN" y "juán" son el mismo, probado por HTTP), y cuenta a los dados de baja. En otro local se puede repetir.
  - 404 "El empleado no existe". 409 "El empleado está dado de baja" (solo en la edición).

### Turnos y mesas

- `PUT /api/v1/admin/empleados/{id}/turnos`: **reemplaza la plantilla semanal entera.** Responde 200 con el
  detalle. La lista vacía lo deja sin turnos.

```json
[{"dia": "LUN", "horaInicio": "12:00", "horaFin": "16:00"}, {"dia": "VIE", "horaInicio": "20:00", "horaFin": "02:00"}]
```

  - **Si `horaFin` es menor que `horaInicio`, el turno termina al día siguiente:** el de 20:00 a 02:00 se guarda
    tal cual. El `dia` es el que empieza. `dia` se acepta en mayúsculas o minúsculas.
  - 400 "El turno no puede empezar y terminar a la misma hora".
  - 400 si dos turnos se pisan, y no se guarda ninguno: "Los turnos del lunes se pisan" (mismo día) o "El turno
    del lunes se pisa con el del martes" (uno que cruza la medianoche contra el del día siguiente, y el del
    domingo contra el del lunes). Dos que se tocan, de 12 a 16 y de 16 a 20, no se pisan.
  - 400 "Falta el día del turno", "El día tiene que ser LUN, MAR, MIE, JUE, VIE, SAB o DOM", "Falta la hora de
    inicio o de fin del turno", "Faltan los turnos" y "Falta un turno".
- `PUT /api/v1/admin/empleados/{id}/mesas` con `{"mesaIds": [5, 6]}`: **reemplaza las mesas del mozo.** Responde
  200 con el detalle. La lista vacía lo deja sin mesas.
  - Una mesa puede tener varios mozos. Los ids repetidos se juntan en uno, y una mesa inhabilitada se puede
    asignar.
  - 404 "La mesa no existe": no existe o es de otro local, y **no se guarda ninguna**.
  - 400 "Solo el mozo tiene mesas asignadas", "Faltan las mesas" y "Falta el id de una mesa".

### Ausencias

Vacaciones, franco o baja temporal. Mientras una ausencia esté vigente, el empleado no sale en la lista del login
ni puede entrar (ver "Qué cambia en el login").

- `GET /api/v1/admin/empleados/{id}/ausencias`: la lista, de la más nueva a la más vieja, sin paginar. Cada una
  es `{id, tipo, desde, hasta}`. `hasta` va en `null` si no tiene fecha de vuelta.
- `POST /api/v1/admin/empleados/{id}/ausencias`: responde 201 con la ausencia.

```json
{"tipo": "VACACIONES", "desde": "2026-12-01", "hasta": "2026-12-15"}
```

  - `tipo` es `VACACIONES`, `FRANCO` o `BAJA_TEMPORAL` (se acepta en minúsculas). `hasta` es opcional.
  - **`desde` y `hasta` cuentan los dos:** el empleado vuelve el día siguiente a `hasta`, solo, sin que nadie
    toque la ausencia. Una de un solo día tiene `hasta = desde`. Los días son del local (hora de Buenos Aires).
  - 400 "Falta el tipo de ausencia", "El tipo tiene que ser VACACIONES, FRANCO o BAJA_TEMPORAL", "Falta la fecha
    de inicio" y "La ausencia no puede terminar antes de empezar".
- `PATCH /api/v1/admin/empleados/{id}/ausencias/{ausenciaId}` con `{"hasta": "2026-12-10"}`: termina la ausencia
  o cambia la fecha de vuelta. Responde 200 con la ausencia.
  - `hasta` es obligatorio (400 "Falta la fecha de fin") y no puede ser anterior a `desde`.
  - 404 "La ausencia no existe": no existe, no es de ese empleado o es de otro local.
- **No hay `DELETE` de ausencias:** terminarla es ponerle `hasta`, así queda de cuándo a cuándo faltó. Una cargada
  por error no se puede anular; lo más que se puede es ponerle `hasta = desde`, y ese día cuenta igual.
- No se controla que dos ausencias se pisen.

### Blanqueo de PIN

- `POST /api/v1/admin/empleados/{id}/pin` con `{"pin": "1234"}`: el PIN provisorio lo escribe el Admin, como en
  el alta (de 4 a 10 dígitos). Responde 200 con el detalle.
  - Guarda el PIN nuevo, vuelve los intentos a 0, **levanta el bloqueo** (es lo que destraba a un mozo que llegó
    al 423) y pone `debeCambiarCredencial: true`: solo va a poder usar `/auth/credencial` hasta que elija el suyo.
  - **`contrasenaProvisoria` no cambia.** El mozo que ya eligió su contraseña no tiene que volver a elegirla: le
    alcanza con el PIN.
  - Se puede blanquear a quien no está bloqueado. Dos blanqueos dejan dos registros de auditoría.
  - Una sesión que el empleado ya tenía abierta queda limitada a `/auth/credencial` desde su próximo request.
    **No invalida la cookie de dispositivo del mozo:** sigue sirviendo hasta que vence.
  - 404 "El empleado no existe". 409 "El empleado está dado de baja". 400 "Falta el PIN", y el 400 genérico si no
    tiene de 4 a 10 dígitos.

### Qué cambia en el login

- **`contrasenaProvisoria`** viaja en `GET /auth/sesion` y en las respuestas de `/auth/pin`, `/auth/admin/login` y
  `/auth/credencial`, después de `debeCambiarCredencial`:

```json
{"id": 4, "nombre": "Juan Pérez", "rol": "MOZO", "local": {"id": 1, "nombre": "La Esquina"},
 "debeCambiarCredencial": true, "contrasenaProvisoria": true, "expiraEn": "2026-11-14T15:00:00Z"}
```

  - Con `debeCambiarCredencial: true` y `contrasenaProvisoria: true` es un **primer ingreso**: el front muestra
    P-32b (elegir contraseña) y P-30 (elegir PIN). Con `debeCambiarCredencial: true` y `contrasenaProvisoria:
    false` es un **blanqueo de PIN**: solo P-30. Cuando `contrasenaProvisoria` es `true`, `debeCambiarCredencial`
    también.
  - Sale de la base en cada llamada, no del token. `local` va en `null` para el Gerente/Dueño.
  - `/auth/mozo/login` y `/auth/dispositivo` no la llevan: no abren sesión. El front la recibe en el `/auth/pin`
    que viene después.
- **`POST /auth/credencial`:**
  - Si el mozo tiene `contrasenaProvisoria: true`, la contraseña pasa a ser obligatoria: sin `password`, 400 "Elegí
    tu contraseña.". Si falta todo, sale primero 400 "Elegí tu PIN.". Al guardarla, `contrasenaProvisoria` pasa a
    `false`.
  - Con `contrasenaProvisoria: false` (después de un blanqueo), alcanza con el PIN, como antes.
  - Caja, Cocina y el Admin no cambian: siempre `contrasenaProvisoria: false`.
- **La ausencia vigente:** `desde <= hoy` y `hasta` vacío o `>= hoy`, con hoy en Buenos Aires.
  - `GET /public/staff/{tokenStaff}` no lista al empleado.
  - `POST /auth/pin` le responde el mismo 404 que a un dado de baja ("El usuario no está en la lista de este
    local."), sin sumar intentos. Se mira antes del bloqueo: un bloqueado con una ausencia vigente ve el 404 y no el
    423.
  - `POST /auth/mozo/login` le responde el 401 genérico "Usuario o contraseña incorrectos", sin sumar intentos y
    sin mirar la contraseña.
  - Al día siguiente de `hasta` vuelve a aparecer y a entrar solo.
  - **Una sesión ya abierta cuando empieza la ausencia no se corta:** la renovación mira que esté activo, no la
    ausencia. Al Admin y al Gerente/Dueño no se les mira la ausencia, porque nadie les puede cargar una.

### Auditoría y cómo se prueba

- Cada cambio deja un registro en la entidad `EMPLEADO`: `CREAR_EMPLEADO`, `EDITAR_EMPLEADO` (solo lo que
  cambió), `BAJA_EMPLEADO`, `CAMBIAR_TURNOS`, `CAMBIAR_MESAS` y `BLANQUEAR_PIN`; y en la entidad `AUSENCIA`,
  `CREAR_AUSENCIA` y `EDITAR_AUSENCIA`. Un cambio que no cambia nada no audita, salvo el blanqueo. Nunca va un PIN,
  una contraseña ni su hash.
- Los tests de HU-14 corren contra el MySQL del Compose, con el login del Admin de verdad (`admin@laesquina.test`)
  y la seed: Juan (Mozo, mesas 1, 2 y 3), Ana (Moza, mesas 3 y 4), Carla (Caja) y Hugo (Cocina) en La Esquina.

## Disponibilidad (para el front)

HU-09, pantallas P-10 (Caja) y P-14 (Admin). Apagar un plato y sacarlo de la carta son lo mismo: el plato deja de
salir en el menú del cliente y los pedidos que ya lo tenían lo siguen mostrando (el ticket también). Las dos rutas
piden el permiso `plato.cambiar_disponibilidad`, que tienen Caja y el Admin: sin sesión dan 401 y con otro rol
(Cocina, el Mozo), 403 "No tenés permiso para hacer esto.". Los errores salen en ProblemDetail.

- `GET /api/v1/platos`: los platos del local, **también los apagados**, en el orden de la carta (categoría y
  después nombre). Es la lista de P-10; Caja no puede usar `GET /admin/platos`.

```json
[
  {"id": 2, "nombre": "Ensalada mixta", "categoria": {"id": 1, "nombre": "Principales"}, "disponible": true},
  {"id": 1, "nombre": "Milanesa napolitana", "categoria": {"id": 1, "nombre": "Principales"}, "disponible": false}
]
```

- `PATCH /api/v1/platos/{id}/disponibilidad` con `{"disponible": false}` o `true`, y el header `X-XSRF-TOKEN`
  (sin él, 403). Responde 200:

```json
{
  "platoId": 1,
  "disponible": false,
  "afectados": [
    {"id": 12, "mesa": "1", "estado": "PENDIENTE_APROBACION", "platos": [
      {"platoId": 1, "nombre": "Milanesa napolitana", "cantidad": 2},
      {"platoId": 3, "nombre": "Flan casero", "cantidad": 1}
    ]},
    {"id": 14, "mesa": "1", "estado": "APROBADO", "platos": [
      {"platoId": 1, "nombre": "Milanesa napolitana", "cantidad": 1}
    ]}
  ]
}
```

  - `afectados` son los pedidos del local con ese plato que todavía se pueden cancelar: `PENDIENTE_APROBACION`
    y `APROBADO`, del más viejo al más nuevo. Con eso se arma el diálogo "Hay 2 pedidos con Milanesa
    napolitana. ¿Los cancelamos?" sin pedir nada más. `mesa` es el número; `platos` trae **todos** los ítems
    del pedido, no solo el plato apagado. Los que están En preparación no salen: siguen en el KDS.
  - Con `disponible: true`, `afectados` va vacío.
  - Mandar el mismo estado que ya tiene responde 200 con los afectados de ahora, pero no guarda, no audita ni
    avisa: un doble toque no deja dos registros.
  - 404 "El plato no existe": no está o es de otro local. Sin `disponible`, 400.
  - Un pedido que el cliente manda justo mientras se apaga el plato puede entrar y no salir en `afectados`.
    Llega a la cola de Caja con ese ítem en `disponible: false`, y se rechaza con `NO_DISPONIBLE`.
- **Cancelar los afectados** (si Caja acepta en el diálogo): `POST /api/v1/caja/pedidos/cancelar` con
  `{"pedidoIds": [12, 14]}`, los ids tal cual vienen en `afectados`. Si Caja no acepta, no se manda nada: el
  plato queda apagado y los pedidos siguen como estaban. Cancela los pedidos enteros, con Caja como quien los
  canceló, y le avisa a cada cliente. Uno por uno es `POST /api/v1/caja/pedidos/{id}/cancelar`. Los dos son de
  German: ver [docs/sprint-2-german.md](docs/sprint-2-german.md).
- **Celulares.** Cuando un plato cambia de verdad, cada celular con la cuenta abierta (o pedida) en ese local
  recibe en `/user/queue/pedido` `PLATO_BAJA` o `PLATO_DISPONIBLE`, después del commit. Al recibir cualquiera
  de los dos, el front vuelve a pedir `GET /public/mesas/{tokenQR}/menu`, como al reconectar. Con `PLATO_BAJA`,
  el carrito saca ese `platoId` si lo tenía:

```json
{"tipo": "PLATO_BAJA", "pedidoId": null, "mesaId": null, "cuentaId": null, "platoId": 1, "estado": null,
 "detalle": null, "ocurridoEn": "2026-11-02T23:03:05Z"}
```

  Todos los eventos del canal llevan ahora `platoId`, en `null` salvo en estos dos.
- Auditoría: `CAMBIAR_DISPONIBILIDAD`, entidad `PLATO`, con `{disponible}` antes y después y quién lo hizo.
- `DisponibilidadDePlatosTest` recorre HU-09 contra el MySQL del Compose (CP-23-01 a CP-23-08, la cancelación
  de CP-23-02 con el lote de German, y CP-25-06), con el PIN de Carla y Hugo y el email del Admin. Lo que viaja
  por el WebSocket lo prueban `CambiarDisponibilidadServiceTest` y `AvisoDeDisponibilidadAdapterTest`.

## Sprint 2: tareas de German

Implementados HU-03 (llamados y cuenta), HU-05 (mesas, avisos, servido y Web Push), prioridad manual de
HU-08 y cancelación individual/en lote de HU-09. Contrato, ejemplos, configuración Push y estado de
validación en [docs/sprint-2-german.md](docs/sprint-2-german.md).

La integración con MySQL y las pruebas desde celulares quedan pendientes por pedido del equipo.

## Fechas

La conexión a MySQL va en UTC (`connectionTimeZone` en `application.yml`), igual que los `Instant` de
Java. Al mirar la base a mano, las columnas `TIMESTAMP` se ven en la zona de tu sesión de MySQL.

## Ramas

`main` estable, `develop` en desarrollo, `feature/...` por tarea. PR a `develop`, lo revisa otro.

## Permisos y auditoría

Para quien arma un endpoint o un Service.

### Pedir un permiso

- Los permisos salen de `permiso` y `rol_permiso` (migración `V2__roles_y_permisos.sql`). Al arrancar, `PermisosPorRolAdapter` los lee y arma en memoria un mapa de rol a permisos. En cada request con sesión, `AutenticadorPorToken` le suma a la sesión una autoridad por cada permiso del rol, con el código tal cual (`pedido.aprobar_rechazar`), además de `ROLE_<rol>`.
- En el endpoint, sobre el método del controlador:

```java
@PreAuthorize("hasAuthority('pedido.aprobar_rechazar')")
```

- Estar logueado no alcanza: sin `@PreAuthorize`, cualquier rol con sesión entra. Cada endpoint nuevo lleva el suyo, con el código copiado de V2: si está mal escrito, nadie entra y el error es un 403 sin explicación.
- Sin el permiso responde 403 con "No tenés permiso para hacer esto."; sin sesión, 401. Ejemplo de prueba: `ControlDePermisosTest`.
- Los permisos se leen una sola vez, al arrancar. Si cambia `rol_permiso` con el back andando, no se entera hasta reiniciar.

### Auditar una acción

- El Service llama al puerto `Auditor` dentro de su propio `@Transactional`:

```java
auditor.registrar(localId, usuarioId, "APROBAR_PEDIDO", "PEDIDO", pedido.getId(),
        Map.of("estado", "PENDIENTE_APROBACION"), Map.of("estado", "APROBADO"));
```

- Los valores anterior y nuevo pueden ser un DTO o un `Map`, y se guardan como JSON. En un alta el anterior va `null`, y queda `NULL` en la base. `entidadId` también puede ser `null`.
- Va en la misma transacción que la acción. Sin transacción abierta falla y no guarda nada, y si falla la escritura del registro la acción tampoco se guarda (CP-33-03, todavía sin confirmar con el grupo).
- El `localId` lo pasa quien llama y no sale de la sesión: el Gerente/Dueño no tiene local, y en HU-16 va el del Admin afectado.
- No se audita lo que hace el cliente anónimo, ni los ingresos y bloqueos.
- Nadie edita ni borra un registro desde la aplicación: el repositorio solo guarda y lee.

## Pendiente

- Consulta de la auditoría con filtros y paginada (HU-15, Sprint 3).
- Retención de 6 meses de la auditoría (RNF-13): hace falta una forma de borrar que hoy el repositorio no tiene.
