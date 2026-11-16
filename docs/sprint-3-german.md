# Sprint 3: tareas de German

Base: `develop` en `32ac755`. Rama: `German-Sprint-3`.
Alcance: filas 49, 50, 53, 54 y 56 de `chit-go-gantt-sprints.xlsx`.
Referencia de coordinación: ST-22 de Ezequiel (14/11); su implementación de HU-14 ya está en develop.

| Tarea | Estado |
|---|---|
| Mesas, QR e inhabilitar (HU-13) | Implementada y validada con MySQL |
| Reportes y consulta de auditoría (HU-15) | Implementada y validada con MySQL |
| Pantalla del salón (HU-17) | Implementada y validada con MySQL y autorización STOMP |
| Carga: RNF-01, RNF-03 y RNF-06 | Ensayo local ejecutado; pendiente red/front del local |
| Integración final y errores (compartida) | Pendiente |

## HU-13: contrato para P-15 y P-38

Todas las rutas bajo `/api/v1/admin/mesas` requieren `mesa.gestionar` y el local de la sesión.
Las escrituras requieren CSRF. Un id de otro local responde 404.

| Método | Ruta | Cuerpo / respuesta |
|---|---|---|
| GET | raíz, `?page=0&size=20` | `{contenido, page, size, totalElementos, totalPaginas}` |
| GET | `/{id}` | Detalle |
| POST | raíz | `{numero}`; 201 con detalle |
| PUT | `/{id}` | `{numero}`; 200 con detalle |
| PATCH | `/{id}/inhabilitada` | `{inhabilitada, motivo}`; 200 con detalle |
| DELETE | `/{id}` | Baja lógica; 204, idempotente |
| GET | `/{id}/qr` | PNG de 512×512 con el UUID, mismo token al reimprimir |

Detalle: `{id, numero, inhabilitada, motivoInhabilitacion, activa, tokenQr}`.
El QR contiene únicamente el UUID, siguiendo HU-13; el lector de la app debe resolver ese token con
`/api/v1/public/mesas/{token}`. El número admite hasta 20 caracteres y es único por local; la base
resuelve también creaciones concurrentes. Duplicado: 409. Las bajas conservan el número reservado
y el historial, y dejan de aparecer tanto en el listado como al escanear o asignar mesas a mozos.

Decisiones de implementación para los puntos abiertos del contrato del 13/10: inhabilitación fija
por Admin, con motivo obligatorio (255 caracteres); rehabilitar borra el motivo. Inhabilitar o dar
de baja con cuenta abierta/solicitada responde 409. El lock es el mismo que usa el escaneo para abrir
cuenta. No se incorpora la inhabilitación temporal del mozo sin una regla de fin de turno acordada.
Cambios e historial se guardan en la misma transacción; repetir el estado sin cambios no audita.

## Coordinación ST-22

Se unifican `PaginaDe` y `PaginaDto` en `compartido`, sin cambiar el JSON de empleados: page desde 0,
size por defecto 20, máximo 100, 400 fuera de rango. `Mesa.id` y `Mesa.localId` se mantienen; la lectura
de usuarios además excluye mesas dadas de baja. Queda previsto documentar las 11 rutas de empleados
en una colección Postman junto con el sprint al hacer la integración final.

Validación HU-13: pruebas HTTP con MySQL real para gestión, permisos, CSRF, aislamiento, cuenta abierta,
baja e historial; lectura del PNG con un decoder QR; regresión de gestión y persistencia de empleados.

## HU-15: reportes y auditoría (P-17 y P-18)

Todas son rutas GET. Requieren `reporte.ver` o `auditoria.ver`, según corresponda. Para Admin el local
sale de su sesión; `localId` no permite ampliarlo. Las mismas rutas bajo `/gerente` requieren `localId`
y comprueban que el local pertenezca al usuario. Un local ajeno responde 404. No se agregan acciones de
gestión del Gerente/Dueño: solo la lectura que comparte HU-15 con HU-16.

- `/api/v1/admin/reportes/ventas?desde=2026-11-01&hasta=2026-11-16&page=0&size=20`:
  `{desde, hasta, totalVentas, cantidadCuentas, detalle}`. `detalle` es una página común de
  `{cuentaId, cerradaEn, total}`. Solo suma cuentas CERRADA por su fecha de cierre; no duplica los
  totales por joins con ítems. El total corresponde al período completo aunque cambie la página.
- `/api/v1/admin/reportes/tiempos-preparacion`, mismos parámetros:
  `{desde, hasta, cantidadPedidos, promedioSegundos, detalle}`. `detalle` pagina
  `{pedidoId, enPreparacionEn, listoEn, segundos}`. Se filtra por `listoEn`, entre preparación y listo,
  aun si luego se sirvió o cerró la cuenta. Excluye marcas incompletas/invertidas. Promedio con dos
  decimales; sin pedidos es `null`, no un tiempo de cero inventado.
- `/api/v1/admin/reportes/platos-mas-pedidos`, mismos parámetros: página común de
  `{platoId, nombre, cantidad}`. Suma unidades por fecha de envío, excluye rechazados y cancelados,
  incluye pendientes y aprobados. Orden: cantidad descendente, id ascendente para desempatar.
- `/api/v1/admin/auditoria?entidad=PEDIDO&accion=APROBAR_PEDIDO&usuarioId=4&desde=2026-11-01&hasta=2026-11-16&page=0&size=20`:
  página común de `{id, usuarioId, usuario, accion, entidad, entidadId, valorAnterior, valorNuevo, creadoEn}`.
  Todos los filtros son opcionales; se combinan con AND. Los valores anterior/nuevo son JSON, no cadenas
  escapadas. Orden estable: fecha e id descendentes. No hay POST, PUT, PATCH ni DELETE de auditoría.

Los reportes requieren ambas fechas. Auditoría permite uno o ninguno de los límites. Las fechas son
inclusivas en `chitgo.reglas.zona-horaria`: desde medianoche de `desde` hasta antes de medianoche del
día siguiente a `hasta`. Un período invertido da 400. Las páginas vacías conservan los metadatos;
ventas sin datos suma 0. El front puede mostrar los mensajes de HU-15 al recibir una lista vacía.

RNF-13: mantenimiento interno a las 03:00 del local, elimina registros anteriores a seis meses de
calendario en lotes de 1000, sin ruta pública. Configuración: `chitgo.auditoria.retencion-habilitada`
(true por defecto) y `retencion-cron`. Las pruebas lo desactivan para evitar limpieza en segundo plano.
La migración V8 agrega índices para los períodos y la retención.

Validación: CP-28-01 ($38.500 y promedio de 15 minutos), totales independientes de la página,
ranking por unidades, exclusión de otros locales y días, filtros, permisos, CSRF, lectura del Dueño
y límite exacto de seis meses; además, regresión de escritura transaccional de auditoría.

## HU-17: pantalla del salón (P-20)

1. El Admin obtiene `{localId, tokenPantalla}` con `GET /api/v1/admin/pantalla` (`local.configurar`).
2. La pantalla, sin sesión de personal, consulta
   `GET /api/v1/pantalla/{tokenPantalla}/pedidos-en-curso`. Devuelve `{localId, pedidos}`;
   cada pedido contiene solamente `{id, mesa, estado}`. Solo EN_PREPARACION y LISTO, de cuentas
   vigentes del local. Una lista vacía permite mostrar «No hay pedidos en preparación».
3. Conecta a `/ws` con el header nativo STOMP `tokenPantalla` y se suscribe a
   `/topic/local/{localId}/pantalla`. Ese principal solo puede escuchar ese destino: no otros locales,
   canales internos ni `/user/queue/pedido`. Tampoco puede publicar SEND.
4. Los eventos públicos contienen únicamente `{tipo, pedidoId, estado, ocurridoEn}`. Ante un evento,
   volver a pedir el snapshot. SERVIDO y CANCELADO retiran el pedido; al reconectar se consulta el
   estado completo. No hay replay. Para evitar perder cambios al iniciar, suscribirse antes de pedir
   el snapshot y volver a consultarlo si llega un evento mientras se carga.

La V9 genera un token propio por local, independiente del QR de staff. Los existentes y los INSERT
por script reciben 18 bytes aleatorios como 36 caracteres hexadecimales; el constructor Java usa UUID.
El token identifica acceso de lectura y debe compartirse solo con la pantalla correspondiente.
No viajan precios, totales, observaciones, personal ni credenciales, tampoco en los eventos. El
servidor filtra el payload explícitamente y publica después del commit.

Validación: 9 pruebas de snapshot/aislamiento, tokens desconocidos o de staff, permiso del Admin,
suscripciones STOMP, prohibición de SEND y publicación pública tras commit. La migración se probó
en MySQL 8.4; agrega la columna antes de rellenarla para evitar el error de ADD con default aleatorio.

## Carga

Perfil reproducible y metodología en [pruebas-de-carga.md](pruebas-de-carga.md).
Resultado del 16/11/2026: 30 mesas, ráfaga de 30 pedidos y un minuto a 10 pedidos/minuto,
sin pérdida ni mezcla de cuentas. p95 servidor → recepción en KDS: **16,44 ms** en ráfaga y
**9,52 ms** sostenido, por debajo de 2000 ms. El ensayo usa HTTP/STOMP reales y MySQL de Docker,
con fixtures aislados que se eliminan al terminar. Evidencia: [resultados-carga-sprint3.json](resultados-carga-sprint3.json).
Esto no verifica la red Wi-Fi/TV ni el render del front; esa comprobación requiere el local.
