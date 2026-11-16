# Sprint 3: tareas de German

Base: `develop` en `32ac755`. Rama: `German-Sprint-3`.
Alcance: filas 49, 50, 53, 54 y 56 de `chit-go-gantt-sprints.xlsx`.
Referencia de coordinación: ST-22 de Ezequiel (14/11); su implementación de HU-14 ya está en develop.

| Tarea | Estado |
|---|---|
| Mesas, QR e inhabilitar (HU-13) | Implementada y validada con MySQL |
| Reportes y consulta de auditoría (HU-15) | Pendiente |
| Pantalla del salón (HU-17) | Pendiente |
| Carga: RNF-01, RNF-03 y RNF-06 | Pendiente |
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
