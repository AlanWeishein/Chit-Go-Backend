# Sprint 2: tareas de German

Base: `develop` en `6d5a092`, con el cierre de cuenta y ticket de HU-10. El alcance sigue las filas
28, 29, 32, 33 y 35 del Excel `chit-go-gantt-sprints.xlsx`. El contrato local
`chit-go-endpoints-api (2).md` sirve de referencia; prevalecen las reglas más nuevas de `develop`,
incluidos los estados que admite `OrigenDeCancelacion.CAJA`.

| Tarea del Excel | Implementación | Validación |
|---|---|---|
| Llamar al mozo y pedir la cuenta (HU-03) | Endpoints, reglas y avisos por cuenta | Pruebas unitarias |
| Mesas del mozo, avisos y servido (HU-05) | Vista propia/salón, atención y servido | Pruebas unitarias |
| Prioridad en la cola de cocina (HU-08) | PUT idempotente, auditoría y evento de reordenamiento | Pruebas unitarias |
| Web Push en los celulares (HU-05) | Suscripciones, VAPID, cifrado y envío asíncrono al mozo | Pruebas unitarias sin red; falta validar desde el front/celular |
| Cancelación en bloque y aviso (HU-09) | Cancelación individual y lote atómico | Pruebas unitarias; falta validar las transacciones con MySQL |
| Pruebas de integración (compartida) | Pendiente | Pospuestas por pedido del usuario |

## Cliente

Las dos rutas llevan `X-Session-Token` y, como todo POST, cookie `XSRF-TOKEN` más header `X-XSRF-TOKEN`.

- `POST /api/v1/public/avisos/llamar-mozo`: sin body; crea el aviso y devuelve `201` con
  `{id, mesaId, mesa, tipo, creadoEn, atendidoPorUsuarioId, atendidoEn}`. Un llamado pendiente en la
  misma cuenta responde `409`, «Ya avisamos al mozo». Una vez atendido se puede volver a llamar.
- `POST /api/v1/public/cuenta/solicitar`: sin body; responde `204`. Solo la sesión iniciadora puede
  pedirla (`403` para lectores). Cualquier pedido pendiente, aprobado, en preparación o listo lo
  impide (`409`). Servidos, rechazados y cancelados no impiden pedirla. Pasa la cuenta a `SOLICITADA`,
  crea un aviso y notifica a Caja. Pedirla otra vez sin un pedido que la reabra da `409`.

Se adoptó que cualquier comensal con sesión vigente puede llamar al mozo, incluidos los lectores,
para que la opción esté disponible en todas las pantallas (punto abierto del contrato del 13/10).
Solo el iniciador solicita la cuenta, por consistencia con el manejo actual del pedido.

Sesión desconocida: `403`; cuenta cerrada: `409`. No se reciben ids de mesa ni de cuenta desde el
cliente: salen de su sesión. Los llamados no duplican avisos si llegan simultáneamente; el segundo
puede recibir el `409` genérico de concurrencia y debe refrescar el estado.

## Mozo

Todas las rutas requieren cookie de sesión. `todas` y `pendientes` son booleanos.

| Método y ruta bajo `/api/v1` | Permiso | Respuesta |
|---|---|---|
| `GET /mozo/mesas?todas=false` | `mesa.ver_asignadas` | Mesas asignadas, incluidas las libres |
| `GET /mozo/mesas?todas=true` | `mesa.ver_asignadas` | Todas las mesas del local, para consultar |
| `GET /mozo/avisos?pendientes=true` | `aviso.atender` | Avisos de sus mesas y cuentas vigentes, del más viejo al más nuevo |
| `GET /mozo/avisos?pendientes=false` | `aviso.atender` | Incluye los atendidos y el historial de sus mesas |
| `POST /mozo/avisos/{id}/atender` | `aviso.atender` | `204` |
| `POST /mozo/pedidos/{id}/servido` | `pedido.marcar_servido` | `204` |

Cada mesa devuelve `{id, numero, inhabilitada, asignada, cuentaId, estado, pedidos}`. Una libre tiene
`cuentaId: null`, `estado: "LIBRE"` y `pedidos: []`. Cada pedido usa el DTO actual de pedidos, con
sus ítems y precios; no se expone el token del cliente ni el QR. `asignada` indica si el mozo puede
actuar sobre la mesa. La vista completa no amplía sus permisos.

Servir solo admite `LISTO → SERVIDO`, guarda usuario y hora y avisa a Cliente, Cocina, pantalla y
mozos. Atender guarda quién y cuándo. Las dos acciones se auditan en la misma transacción y responden
`403` para una mesa ajena, `404` para ids de otro local o inexistentes y `409` si la acción ya no
corresponde. Un aviso de una cuenta cerrada no se atiende sobre una cuenta nueva de esa mesa.

## Prioridad y cancelación

- `PUT /api/v1/caja/pedidos/{id}/prioridad`, con `{"prioridadManual": true}` o `false`:
  requiere `pedido.priorizar`, responde `204`. Solo admite pedidos `APROBADO`, que todavía esperan
  en la cola. No cambia la fecha de llegada. Repetir el valor actual no genera auditoría ni evento.
  El GET de Cocina sigue usando la `PriorityQueue` existente: prioritarios primero y luego llegada.
- `POST /api/v1/caja/pedidos/{id}/cancelar`, sin body: requiere `pedido.cancelar`, responde `204`.
  Admite pendientes y aprobados, siguiendo `OrigenDeCancelacion.CAJA` de develop. Se cancela el
  pedido entero, sin cancelación parcial de ítems. El permiso también lo tiene Admin como respaldo.
- `POST /api/v1/caja/pedidos/cancelar`, con `{"pedidoIds": [12, 13]}`: extensión para la selección
  en bloque de HU-09. Misma autorización y respuesta; de 1 a 100 ids positivos y distintos. Se
  validan todos antes de modificar: un id ajeno/inexistente da `404`; uno no cancelable da `409`.
  No se omiten silenciosamente pedidos que cambiaron desde que Caja abrió la pantalla.

El lote es una sola transacción. Cuenta, pedidos y auditoría compiten con el cierre y las transiciones
de Cocina; un conflicto devuelve `409` y se deshace el lote. Los eventos STOMP salen después del
commit. Para validar esto contra commits y locks reales quedan las pruebas de integración.

Eventos nuevos: `CUENTA_SOLICITADA` a Caja; `AVISO_CREADO` y `AVISO_ATENDIDO` a mozos;
`PRIORIDAD_CAMBIADA` a Cocina y Caja; `SERVIDO` a Cliente, Cocina, pantalla y mozos.
Las cancelaciones usan `PEDIDO_CANCELADO` en canales internos y `CANCELADO` para Cliente/pantalla.
En `AVISO_CREADO`, `cuentaId` identifica la cuenta y `detalle` lleva el tipo de aviso. Ante eventos
de avisos se vuelve a consultar su lista; el evento no lleva `avisoId`. Al reconectar se piden
los GET de estado: no hay replay.

## Web Push

Está apagado por defecto. Para activarlo, configurar en el proceso Java (no alcanza el `.env` de
Compose): `PUSH_HABILITADO=true`, `PUSH_CLAVE_PUBLICA`, `PUSH_CLAVE_PRIVADA` y `PUSH_CONTACTO`
(`mailto:...` o URL HTTPS de contacto). Las claves forman un par VAPID P-256 en Base64 URL-safe;
la privada queda únicamente en el backend. La biblioteca empleada es
[webpush-java](https://github.com/web-push-libs/webpush-java), con Bouncy Castle y el cliente HTTP
de Java 21. No se generaron ni guardaron claves reales en el repositorio.

El front registra su service worker y solicita el permiso del navegador en una acción del usuario.
Luego:

1. `GET /api/v1/mozo/push/clave-publica` devuelve `{habilitado, clavePublica}`.
2. `pushManager.subscribe({userVisibleOnly: true, applicationServerKey: ...})` produce la suscripción.
3. `POST /api/v1/mozo/push/suscripcion` recibe el JSON del navegador:

   ```json
   {"endpoint": "https://fcm.googleapis.com/...", "keys": {"p256dh": "...", "auth": "..."}}
   ```

4. `DELETE /api/v1/mozo/push/suscripcion`, con `{"endpoint": "..."}`, la elimina solo para el
   usuario/local de la sesión. El front debe hacerlo antes del logout en un dispositivo compartido.

Las tres rutas requieren `aviso.atender`; POST y DELETE llevan CSRF. POST responde `204`, o `409`
si Push está apagado. Registrar de nuevo el mismo endpoint actualiza sus claves/destinatario sin
duplicarlo; al cambiar de mozo en el mismo navegador hay que registrarlo con la sesión nueva.
Los endpoints solo admiten HTTPS y hosts explícitamente permitidos. `PUSH_HOSTS_PERMITIDOS` permite
cambiar la lista de proveedores; por defecto incluye FCM, Mozilla y Apple.

Un `AVISO_CREADO` o `PEDIDO_LISTO` inicia el envío después del commit, en un executor limitado.
Se notifica únicamente al mozo elegido en `cuenta.mozoId`, convertido de empleado a usuario, que
siga activo y asignado a la mesa; se envía a todas sus suscripciones. Sin mozo no hay Push y sigue
existiendo la alerta de mesa sin mozo. El payload es `{titulo: "Chit&Go", evento: {...}}`, sin
tokens de sesión ni precios. El service worker del front debe mostrar la notificación y volver
a consultar el estado al abrir la app.

Los envíos tienen timeout y TTL de 120 segundos. Un `404` o `410` del proveedor elimina la
suscripción vencida. Los fallos de red no deshacen la acción ni borran la suscripción. El envío es
de mejor esfuerzo, sin reintentos persistentes: si el proceso se detiene o se llena la cola,
puede perderse una notificación. El estado se recupera por REST. Falta validar permisos del navegador,
HTTPS/service worker y recepción con el celular en segundo plano junto con el front.

## Migraciones y validación

`V4` agrega `aviso.cuenta_id` y `version`, para separar ocupaciones de una mesa y evitar doble
atención. Los avisos históricos sin cuenta se conservan, pero no aparecen como pendientes ni se
atienden desde las rutas nuevas. `V5` crea las suscripciones Push. La seed de dev ahora también
limpia esas suscripciones: como ya advierte el README, el cambio de una seed repetible hace que
Flyway la vuelva a aplicar y borre los datos de negocio de desarrollo al próximo arranque con `dev`.

Se agregaron pruebas unitarias de reglas, permisos sobre mesas, selección de destinatarios,
validación de endpoints/claves, cifrado VAPID, fallos de proveedores y publicación tras commit.
No se ejecutaron pruebas de integración, migraciones contra MySQL ni envíos reales de Push.

Resultado: **297 pruebas unitarias, sin fallos**. Para repetir únicamente las unitarias actuales
desde PowerShell, excluyendo las clases que levantan el contexto de Spring:

```powershell
$conContexto = @(rg -l '@SpringBootTest|@DataJpaTest|@WebMvcTest|@SpringJUnitConfig' src/test)
$unitarias = @(rg --files src/test -g '*Test.java' |
    Where-Object { $conContexto -notcontains $_ } |
    ForEach-Object { [IO.Path]::GetFileNameWithoutExtension($_) })
mvn ('-Dtest=' + ($unitarias -join ',')) test
```
