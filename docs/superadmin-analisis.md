# SuperAdmin: inventario y análisis previo

Fecha: **16 de noviembre de 2026**. Estado: **solo análisis; propuesta no implementada**.

## 1. Resultado principal

El proyecto implementa cinco roles: `GERENTE_DUENO`, `ADMIN`, `MOZO`, `CAJA` y `COCINA`.
No hay un rol de plataforma implementado ni un acceso administrativo global.

**Sí existe una referencia explícita al Super Admin como trabajo de MVP 2.** Está en
[AltaDeLocalService.java](../src/main/java/com/chitgo/usuarios/aplicacion/AltaDeLocalService.java#L29):
explica que hoy la auditoría del script registra al Gerente/Dueño y no identifica a la persona
que ejecutó el comando; esa atribución queda para el «Super Admin del MVP 2».

La evidencia respalda esa dirección futura, pero no define una matriz de permisos, contratos HTTP,
pantallas, recuperación de credenciales o acceso general a todos los restaurantes.
No alcanza para inventar esas capacidades ni para adelantar automáticamente MVP 2 a la presentación.

**Decisión recomendada:** conservar el alcance actual de MVP 1. Si se aprueba un experimento,
delimitarlo al actor identificable que ejecuta las dos operaciones ya existentes por script:
alta de local con su Gerente y desbloqueo de Gerente. Mantener la operación de cada restaurante
en Admin/Gerente y revisar la propuesta antes de programar.

## 2. Base de Git y límites de esta ejecución

- Se verificó el estado y se ejecutó `git fetch origin`.
- Base: **origin/develop**, commit **a7cb9012b89f688131e57123917006569255a6bc**.
- Rama experimental creada: **feature/superadmin**.
- Git confirmó que `German-Sprint-3` es ancestro de la base: la comparación
  `origin/develop...German-Sprint-3` dio **19 / 0** commits. No faltan commits de esa rama.
- La base incluye las integraciones de HU-16: Admins del Gerente, alta por script, desbloqueo
  de Gerente y reportes/auditoría de Gerente. Usar la rama vieja habría omitido esas capacidades.
- La mitigación de login continúa en `fix/account-lockout-abuse`
  (`ceaa2b6`, `d6718a7`); esos commits no se integraron en esta rama.
- El archivo local previo `docs/resumen-trabajo-2026-11-16.md` se conserva sin editar.
- El análisis no modifica código, configuración, permisos, migraciones, seeds ni pruebas.
  German autorizó posteriormente publicar el trabajo experimental en una rama separada.
  No se integra a develop ni se abre un PR automáticamente.

La evidencia del informe es **inspección estática**. No se ejecutaron tests, comandos de alta,
migraciones ni operaciones sobre MySQL. Los resultados anteriores de otras ramas no validan esta base.

## 3. Fuentes e inventario del repositorio

Se revisaron POM, perfiles, módulos de aplicación y dominio, controladores, repositorios, seguridad,
migraciones V1–V9, seed de desarrollo, tests relacionados, README, documentación de sprints y Postman.
El inventario de controladores encontró **85 mappings HTTP**; los que declaran permisos
figuran en el anexo. Esta cifra describe el código fuente, no un relevamiento dinámico del servidor.

Fuentes de negocio disponibles: referencias RF/HU/RNF/CP en código, tests y migraciones,
[README](../README.md), [Sprint 2](sprint-2-german.md), [Sprint 3](sprint-3-german.md),
[instrucciones Postman](postman/README.md) y la colección JSON.

No se encontraron PDF, Excel o DOCX en el árbol de archivos inspeccionado. Los documentos mencionan
Figma, historias, un Excel de planificación y contratos externos, pero sus contenidos completos
no están incorporados al repositorio: no se los trató como requisitos consultados.
El adjunto de esta tarea fija el procedimiento de análisis, no demuestra que una función de negocio
ya exista.

**Discrepancia documental:** el texto inicial de HU-15 en `docs/sprint-3-german.md` y Postman pide
`localId` para Gerente. El README actualizado, `ReportesGerenteController`,
`AuditoriaGerenteController` y sus tests admiten omitirlo y lo resuelven por propietario.
Se documenta aquí el comportamiento del código actual sin cambiar esos archivos.

## 4. Arquitectura observada

Java **21**, Spring Boot **4.0.8**, Spring Security, JPA/JDBC, MySQL y Flyway.
Monolito modular con paquetes `usuarios`, `menu`, `pedidos`, `reportes`, `auditoria` y `compartido`.
Cada módulo separa dominio/puertos, aplicación y adaptadores de infraestructura.
Los servicios implementan transacciones y las reglas de autorización por local; los controllers
declaran permisos y traducen HTTP a casos de uso.

La autenticación produce `UsuarioAutenticado` con usuario, rol y local opcional.
Las autoridades se agregan desde las tablas de permisos, además de `ROLE_<rol>`.
**No hay jerarquía automática de roles:** Gerente no hereda los permisos del Admin.
Tener permiso tampoco reemplaza los filtros de local o propietario de los servicios.

No se usa una política global que haga que un usuario «sin local» vea todos los locales.
Por el contrario, varios casos de uso rechazan un `localId` de sesión nulo.

## 5. Roles actuales

Todos están definidos como datos fijos en
[V2](../src/main/resources/db/migration/V2__roles_y_permisos.sql) y reconocidos por
[Rol.java](../src/main/java/com/chitgo/usuarios/dominio/Rol.java).

| Rol exacto | Autenticación | Alcance real | Permisos |
|---|---|---|---|
| `GERENTE_DUENO` | Email y contraseña en `POST /api/v1/auth/admin/login` | Sesión con local nulo; opera sobre el restaurante que lo tiene como propietario. No opera Caja/Cocina/Mozo ni administra cualquier restaurante | 3: `admin.gestionar`, `reporte.ver`, `auditoria.ver` |
| `ADMIN` | Mismo login por email | Local de su fila de usuario y sesión. Gestiona empleados, carta, mesas, pantalla y consultas; no crea ni desbloquea otros Admins | 8; tabla siguiente |
| `MOZO` | Usuario/contraseña del local para habilitar dispositivo y luego PIN mediante QR de staff | Su local y sus mesas asignadas para actuar. Puede consultar el salón de su local sin ganar permisos sobre otras mesas | 3: `mesa.ver_asignadas`, `pedido.marcar_servido`, `aviso.atender` |
| `CAJA` | PIN desde QR de staff | Cola, prioridad, disponibilidad, cancelación y cierre/ticket de su local | 5; tabla siguiente |
| `COCINA` | PIN desde QR de staff | Cola y transiciones de preparación/listo de su local | 1: `pedido.cambiar_estado_cocina` |

Cliente anónimo y pantalla pública son identidades adicionales basadas en tokens; **no son filas
de rol** de V2. El operador del script tampoco es hoy una identidad autenticada del sistema.

### 5.1 Permisos, relación y verificación

V2 define **16 permisos** y **20 asociaciones** en `rol_permiso`.
`Rol.permisos` usa esa tabla. `PermisosPorRolAdapter` carga los permisos una vez al arrancar;
`AutenticadorPorToken` agrega las autoridades a la autenticación y
`@PreAuthorize` controla los endpoints. Un cambio de permisos en la base requiere reiniciar
para que la caché lo refleje.

| Permiso exacto | Roles de V2 | Endpoints / controladores observados |
|---|---|---|
| `admin.gestionar` | `GERENTE_DUENO` | `AdminsGerenteController` |
| `reporte.ver` | `GERENTE_DUENO`, `ADMIN` | `ReportesController`, `ReportesGerenteController` |
| `auditoria.ver` | `GERENTE_DUENO`, `ADMIN` | `AuditoriaController`, `AuditoriaGerenteController` |
| `empleado.gestionar` | `ADMIN` | `AlertasController`, `EmpleadosAdminController` |
| `menu.gestionar` | `ADMIN` | `CategoriasAdminController`, `EtiquetasAdminController`, `PlatosAdminController` |
| `mesa.gestionar` | `ADMIN` | `MesasAdminController` |
| `local.configurar` | `ADMIN` | `PantallaController` |
| `plato.cambiar_disponibilidad` | `ADMIN`, `CAJA` | `DisponibilidadController` |
| `pedido.cancelar` | `ADMIN`, `CAJA` | `GestionDeColaController` |
| `mesa.ver_asignadas` | `MOZO` | `MozoController` |
| `pedido.marcar_servido` | `MOZO` | `MozoController` |
| `aviso.atender` | `MOZO` | `MozoController`, `PushController` |
| `pedido.aprobar_rechazar` | `CAJA` | `AlertasController`, `CajaController` |
| `pedido.priorizar` | `CAJA` | `GestionDeColaController` |
| `cuenta.cerrar` | `CAJA` | `CuentasController` |
| `pedido.cambiar_estado_cocina` | `COCINA` | `CocinaController` |

El anexo enumera métodos y rutas, incluyendo las autorizaciones declaradas a nivel de clase.
`CodigosDePermiso` contiene solo algunas constantes usadas por STOMP; **no es el catálogo completo**.

Un permiso es una capacidad concreta: no alcanza con asignar todos los permisos actuales a un actor
nuevo. Los servicios todavía exigirían local de sesión o propiedad. Tampoco corresponde eliminar
esas validaciones para facilitar un nuevo rol.

### 5.2 Diferencia entre permiso previsto y función implementada

V2 describe `local.configurar` para tiempo de inactividad y regeneración del QR de staff.
En el código actual, su endpoint localizado es **GET /api/v1/admin/pantalla**.
No se encontraron rutas o servicios de escritura que cambien
`Local.minutosInactividadSesion` o regeneren `tokenStaff` de un local existente.

El campo y la generación inicial de token sí existen. Esta diferencia es un faltante respaldado
por la descripción del permiso, pero **no forma parte de un SuperAdmin implementado** ni autoriza
a incluir esa tarea automáticamente en este experimento.

## 6. Modelo de locales e aislamiento

Fuentes: [Local.java](../src/main/java/com/chitgo/usuarios/dominio/Local.java),
[V1](../src/main/resources/db/migration/V1__esquema_inicial.sql),
[V3](../src/main/resources/db/migration/V3__local_id_en_tablas_puente.sql) y
[V9](../src/main/resources/db/migration/V9__token_de_pantalla.sql).

| Campo actual | Significado y restricciones |
|---|---|
| `id` | BIGINT autoincremental |
| `propietario_usuario_id` | Obligatorio, FK a usuario; el flujo de alta crea un Gerente |
| `nombre` | Obligatorio, hasta 120 caracteres |
| `direccion` | Opcional, hasta 255 |
| `token_staff` | CHAR(36), obligatorio y único; QR del login de personal |
| `token_pantalla` | CHAR(36), obligatorio y único; acceso de lectura de pantalla |
| `minutos_inactividad_sesion` | SMALLINT, por defecto 10; duración usada para sesión del local |
| `creado_en` | Timestamp de creación |

**No existen campos de suspensión, baja o estado activo del local.**
La asociación de propietario es un id en Java y una FK en SQL.
Usuarios, empleados, mesas, carta, cuentas, pedidos, sesiones de cliente y auditoría se vinculan al local.

MVP 1 supone un local por Gerente, pero **no hay UNIQUE sobre propietario**:
`buscarPorPropietario` usa
`findFirstByPropietarioUsuarioIdOrderByIdAsc`. Si hubiera varios, toma el de menor id;
el propio puerto marca esa situación para revisar en MVP 2. No convertir esta limitación en una
autorización global.

Resolución de ámbito:

- Admin y operadores: `localId` sale de la sesión; los repositorios reciben ese ámbito al buscar entidades.
- Rutas `/gerente/...`: propiedad resuelta por `LocalDelGerenteService` o
  `GestionarAdminsService`; en el MVP actual se elige el primer local del propietario.
- Lecturas `/admin/reportes` y `/admin/auditoria`: `LocalDeConsulta` usa el local de sesión o,
  para Gerente sin local, verifica expresamente la propiedad del `localId` recibido.
  Esa lectura puede resolver un local propio indicado explícitamente; no habilita locales ajenos.
- Clientes: QR y token de sesión determinan mesa/cuenta/local, con controles en los servicios.
- Pantalla: el token determina el local; no se permite elegir arbitrariamente otro con un parámetro.
- V3 agrega `local_id` a tablas puente. Declara expresamente que la coincidencia de locales se controla
  en el servicio, sin FK compuesta. La base por sí sola no reemplaza el aislamiento de aplicación.

### 6.1 Alta actual

`ComandosDelLocal`, activo solo en perfil `script`, ofrece `alta-local`.
`AltaDeLocalService.darDeAlta` valida datos y email único, crea el Gerente sin local y luego el local
que lo apunta, dentro de una transacción. La contraseña es BCrypt y provisoria; se debe cambiar
al ingresar. El token staff se genera al dar de alta; el token de pantalla se crea por el modelo/default.

El script recibe la contraseña por entorno o consola; rechaza argumentos de contraseña.
El perfil corre en loopback y puerto libre, no ejecuta Flyway y termina al completar el comando.
**No existe hoy un endpoint HTTP de alta de locales ni un listado administrativo global.**

El acceso al script depende del acceso operativo al proceso/base; no autentica a una persona
con un rol de plataforma. No se ejecutaron esos comandos durante el análisis.

## 7. Modelo y ciclo de vida de usuarios

`usuario` contiene id, local opcional, rol obligatorio, nombre, nombre de usuario, email,
hash de contraseña, hash de PIN, fallos, bloqueo, activo, marcas de credencial provisoria,
`sesion_valida_desde` y fecha de creación. V1 exige al menos un hash de credencial;
V6 agrega los dos campos de credencial/sesión. El email es único en todo el sistema;
el nombre de usuario de mozo es único dentro del local.

- **Gerente:** `Usuario.nuevoGerente` y alta por script, con `localId = null`.
- **Admin:** `Usuario.nuevoAdmin`, creado por Gerente en su local y con fila de empleado.
- **Mozo/Caja/Cocina:** `Usuario.nuevoEmpleado`, creado por Admin con usuario y empleado asociados.
  `Empleado.ROLES_GESTIONABLES` contiene exactamente esos tres roles.
- El esquema admite local nulo; los flujos actuales lo usan para Gerente. Esto no demuestra que ya
  exista otro tipo de usuario global.
- Edición de empleado: nombre y usuario del mozo; no cambia rol, PIN ni contraseña por esa ruta.
  El PIN tiene su caso de blanqueo. Admins tienen alta, activo y desbloqueo; no un CRUD genérico de usuarios.
- Baja de usuario/empleado es lógica. Gerente puede reactivar Admin; no hay endpoint genérico de
  reactivación de empleados ni de gestión de Gerentes.
- RNF-07 conserva cinco fallos y el centinela de bloqueo `2038-01-01T00:00:00Z`.
  Admin blanquea PIN de empleados; Gerente desbloquea Admin; el script desbloquea Gerente.
- No existe hoy una cuenta superior que recupere un hipotético SuperAdmin.
- `ElegirCredencialService` utiliza el tipo de login del rol para exigir PIN o contraseña.
  Añadir una fila de rol en SQL por sí sola no la habilitaría para el login por email.

Fuentes: `Usuario`, `Empleado`, `GestionarEmpleadosService`, `GestionarAdminsService`,
`AltaDeLocalService`, `IngresarConEmailService`, `IngresarConPinService`,
`HabilitarDispositivoService` y `ElegirCredencialService`.

## 8. Capacidades reales de Admin

| Capacidad implementada | Endpoint o familia | Servicio / límites |
|---|---|---|
| Empleados | `/api/v1/admin/empleados`: listado paginado, detalle, alta, edición y baja | `GestionarEmpleadosService`; solo Mozo/Caja/Cocina del propio local |
| Organización de empleados | Turnos, mesas de mozo, ausencias, fin de ausencia y PIN, bajo empleado | Mismo servicio; valida pertenencia y rol; no convierte empleados en Admin |
| Carta | Platos, destacados, categorías/orden y etiquetas bajo `/api/v1/admin` | `GestionarPlatosService`, `GestionarCategoriasService`, `GestionarEtiquetasService`; ámbito propio |
| Disponibilidad | `GET /api/v1/platos`, `PATCH /api/v1/platos/{id}/disponibilidad` | `CambiarDisponibilidadService`, compartido con Caja |
| Cancelación de pedidos | Individual y lote bajo `/api/v1/caja/pedidos` | `GestionDeColaService`, compartido con Caja; aplica las transiciones admitidas, no implica permiso para aprobar o cerrar cuentas |
| Mesas y QR | Listado/detalle, alta, edición, inhabilitación, rehabilitación, baja lógica y QR PNG | `GestionarMesasService`, `MesasAdminController`; no se puede inhabilitar o dar de baja con cuenta abierta |
| Reportes | Ventas, tiempos y platos más pedidos en `/api/v1/admin/reportes` | `ConsultarReportesService` y `LocalDeConsulta`; solo local permitido |
| Auditoría | `GET /api/v1/admin/auditoria` | `ConsultarAuditoriaService`; lectura por local y filtros |
| Pantalla | `GET /api/v1/admin/pantalla` | `PantallaService.acceso`; obtiene token de su local |
| Mesas sin mozo | `GET /api/v1/alertas/mesas-sin-mozo` y topic de alertas | `empleado.gestionar` habilita ese canal; no habilita todos los topics |

Admin **no tiene** `admin.gestionar`, `pedido.aprobar_rechazar`, `pedido.priorizar`,
`cuenta.cerrar`, `pedido.cambiar_estado_cocina` ni los permisos de Mozo.
Las rutas bajo `/caja` no son exclusivas de Caja por su nombre: cancelación también la usa Admin,
porque el permiso lo permite. Los otros permisos no se heredan.

Repositorios relevantes: usuarios/empleados por local y rol, `MesaRepository`,
repositorios de carta y `PedidoRepository` a través de los servicios anteriores.
No hay un repositorio global de Admin.

## 9. Capacidades reales de Gerente/Dueño

`AdminsGerenteController` + `GestionarAdminsService`:

- Listar Admins propios, incluidos inactivos, sin paginado.
- Crear varios Admins con email único y contraseña provisoria.
- Dar de baja o reactivar Admin, sin borrado físico.
- Desbloquear un Admin activo; si está dado de baja, exige reactivarlo primero.
- Un id inexistente, de otro local o de otro rol responde con el mismo 404.

`ReportesGerenteController` y `AuditoriaGerenteController`:

- Consultar ventas, tiempos, ranking y auditoría de su local.
- Las cuatro lecturas usan permisos y `LocalDelGerenteService`, que exige además
  el rol exacto `GERENTE_DUENO`.
- `localId` es opcional; si viene y no es el propio, se rechaza.
- También puede usar las lecturas `/admin/reportes` y `/admin/auditoria` indicando un local propio.
- No tiene rutas de escritura de reportes ni auditoría, ni operaciones de carta, personal o mesas.

**No es un administrador global.** Comparte algunas responsabilidades administrativas con la idea
de un actor superior, pero sus capacidades están limitadas por propiedad. Darle acceso global
alteraría la responsabilidad del propietario y los supuestos de HU-16.

## 10. Auditoría

`registro_auditoria` exige **local_id y usuario_id no nulos**, con FK; registra acción,
entidad/id, JSON anterior/nuevo y fecha. `AuditorAdapter` exige una transacción existente
(`Propagation.MANDATORY`), por lo que acción y registro se confirman o revierten juntos.

Acciones localizadas por familias:

| Área | Acciones o hechos auditados |
|---|---|
| Script | `CREAR_LOCAL`, `DESBLOQUEAR_GERENTE`, con origen script |
| Admins | `CREAR_ADMIN`, `BAJA_ADMIN`, `REACTIVAR_ADMIN`, `DESBLOQUEAR_ADMIN` |
| Personal | `CREAR_EMPLEADO`, `EDITAR_EMPLEADO`, `BAJA_EMPLEADO`, `BLANQUEAR_PIN`, `CAMBIAR_TURNOS`, `CAMBIAR_MESAS` |
| Ausencias | `CREAR_AUSENCIA`, `EDITAR_AUSENCIA` |
| Carta | Crear/editar platos y destacados; crear/editar/borrar categorías y etiquetas; ordenar categorías; `CAMBIAR_DISPONIBILIDAD` |
| Mesas | `CREAR_MESA`, `EDITAR_MESA`, `INHABILITAR_MESA`, `HABILITAR_MESA`, `BAJA_MESA` |
| Operación | Aprobar/rechazar, prioridad, cancelación, preparación/listo, servir, atender aviso y cierre de cuenta |

Las lecturas usan `LocalDeConsulta` y una consulta con `WHERE a.local_id=:local`.
Filtros: entidad, acción, usuario y fechas; paginación y orden estable por fecha/id.
No hay endpoint de edición o borrado. La retención elimina registros anteriores a seis meses
de calendario en lotes de 1000, con tarea interna a las 03:00 configurable.

No se auditan todos los eventos posibles: por ejemplo login y fallos de credenciales no llaman
al puerto Auditor. Que la retención recorra toda la tabla no crea una facultad global para un rol.

**Carencia concreta:** el script atribuye la acción al Gerente afectado, con origen script;
no identifica a la persona operadora. Un futuro usuario de plataforma podría figurar como actor
en un registro asociado al local afectado sin necesitar un registro con local nulo.
Eso es viabilidad estructural; aún no es un caso de uso implementado.

Una vista de auditoría global requeriría autorización y consultas específicas; hoy no existe.
No corresponde quitar el filtro por local de la consulta usada por Admin/Gerente.

## 11. Seguridad que condiciona cualquier propuesta

- Email: solo los roles de `Rol.CODIGOS_CON_EMAIL`; contraseña BCrypt, validación de email/tamaños,
  errores genéricos 401, cuenta activa y bloqueo RNF-07.
- PIN: Mozo/Caja/Cocina con QR del local; Mozo requiere además habilitación del dispositivo.
  No es un método de acceso de un actor global.
- JWT HS256 con usuario, rol, local opcional, marcas, emisión y expiración. Si falta la clave configurada,
  se genera una al azar. No se copiarán claves ni credenciales a este documento o al código.
- Cookie `chitgo_sesion`: HttpOnly, SameSite=Strict, Path=/; Secure configurable y desactivado en dev.
  Cookie de dispositivo con ámbito distinto y vencimiento de siete días por defecto.
- CSRF SPA para escrituras y logins. Credenciales provisorias limitadas a sesión, cambio y logout.
- Sesión: T del local para operadores/Admin; Caja una hora; sin local, diez minutos por defecto.
  Renovación relee usuario activo y datos actuales; logout registra `sesion_valida_desde`.
- Las rutas públicas son ping, funcionalidades anónimas bajo `/public`, snapshot de pantalla,
  handshake `/ws`, logins, dispositivo y error; son públicas en Spring Security, pero sus servicios
  pueden exigir tokens propios.
- STOMP: CONNECT selecciona token de pantalla, luego token de cliente y finalmente JWT del handshake.
  SUBSCRIBE verifica principal, destino, local y permiso. El código rechaza SEND de clientes.
  Un principal de staff sin local no puede suscribirse a topics de restaurante.
- Pantalla usa token independiente y snapshot por local; expone pedidos visibles, no credenciales.
  Tener el token no convierte al portador en staff.
- `AutenticadorPorToken` y la renovación HTTP no hacen exactamente las mismas comprobaciones;
  no se debe asumir que añadir un rol habilita por igual todos los canales. Cualquier futura
  identidad privilegiada requiere pruebas de inactividad, baja, logout y WebSocket explícitas.
- No hay rate limiting integrado en esta base. La rama de mitigación sigue separada;
  no se la debe presentar como parte de este análisis ni integrarla sin una decisión posterior.

Estas son observaciones de código y tests existentes, no una nueva auditoría dinámica de seguridad.

## 12. Migraciones existentes

| Versión | Contenido relevante |
|---|---|
| V1 | Esquema, usuario/local, FKs, credenciales, auditoría por local |
| V2 | Cinco roles, 16 permisos, 20 asociaciones |
| V3 | local_id en tablas puente; aislamiento adicional a cargo de servicios |
| V4 | Avisos vinculados a cuenta |
| V5 | Suscripciones Web Push |
| V6 | Contraseña provisoria y revocación por fecha de sesión |
| V7 | Baja lógica y disponibilidad de mesas |
| V8 | Índices de reportes/auditoría y retención |
| V9 | Token de pantalla separado y único |

No hay migración de SuperAdmin.
El análisis no requiere ninguna migración.
Una propuesta futura con un rol nuevo requeriría **una migración nueva** de datos para rol,
permisos y asociaciones aprobados; nunca editar V2 aplicada. El número se elegirá con la base
actualizada al implementar, sin reservar ahora el siguiente número.

Usuario ya admite local nulo y la auditoría admite actor de otro local por FK simple.
No se demuestra necesidad de nuevas tablas o columnas para las dos acciones delimitadas.
Un estado de suspensión de local o una auditoría sin local exigirían otras decisiones/esquema
y carecen de respaldo funcional suficiente aquí.

## 13. Evidencia específica de SuperAdmin

Búsqueda previa a crear este documento: `SUPERADMIN`, `SUPER_ADMIN`, `super admin`,
`administrador global` y `admin global` en código, tests, migraciones y documentación.
La única coincidencia de concepto encontrada fue el comentario de
`AltaDeLocalService.java:29–35`.

| Concepto | Clasificación | Evidencia y alcance |
|---|---|---|
| Rol, sesión o endpoints de SuperAdmin actuales | **NO RESPALDADO como implementación** | No aparecen en V2, allowlists de Rol, controllers o tests |
| Identificar quién ejecutó el script mediante futuro Super Admin | **RESPALDADO, limitado a una referencia de MVP 2** | Comentario de AltaDeLocalService; no es especificación completa |
| Alta de local con Gerente y desbloqueo del Gerente | **EXISTE** | AltaDeLocalService, ComandosDelLocal, perfil script y tests |
| Gestión de Admins por su propietario | **EXISTE** | GestionarAdminsService y AdminsDelGerenteTest |
| Reportes/auditoría de un local autorizado | **EXISTE** | Servicios, LocalDeConsulta y ReportesDelGerenteTest |
| Configurar T o regenerar QR por Admin | **RESPALDADO por descripción de permiso; flujo de escritura no encontrado** | V2 local.configurar; distinto de SuperAdmin |
| Suspender/borrar locales, impersonar o dashboard global | **NO RESPALDADO** | Sin casos de uso, contratos ni modelo correspondiente |
| Conceder acceso a todo restaurante por tener local nulo | **NO RESPALDADO** | Contradice las comprobaciones actuales |
| Convertir al Gerente en rol de plataforma | **NO RESPALDADO** | HU-16 lo limita al propietario/local |

«NO RESPALDADO» describe la evidencia disponible, no afirma que sea imposible decidirlo
como requisito nuevo. Todo cambio de alcance requiere una decisión explícita.

## Propuesta de SuperAdmin

### Confirmado por el proyecto

Cinco roles actuales; un Gerente propietario del ámbito de trabajo del MVP; scripts de alta de
local y desbloqueo de Gerente; auditoría transaccional con actor/local; referencia futura a
identificar al operador mediante Super Admin en MVP 2.

### Reutilizable

Validación de alta, unicidad de email y traducción de conflictos; BCrypt y primer ingreso;
sesión/JWT/cookies/CSRF; permisos por tablas; lógica de alta y desbloqueo;
transacciones y Auditor; paginación y convenciones ProblemDetail; fixtures y tests de aislamiento.

Reutilizar lógica requiere adaptar el contexto del actor donde hoy está implícito el script.
No basta con exponer directamente AltaDeLocalService por HTTP.

### Faltante pero respaldado

La identificación de la persona ejecutora del script está explícitamente diferida a MVP 2.
No hay un backlog completo de SuperAdmin para este MVP. La configuración T/QR mencionada en V2
es otro faltante y no se incorpora por asociación al nombre «administrador».

### Idea opcional — PROPUESTA NO IMPLEMENTADA

**Alternativa A: conservar MVP 1.** Mostrar los roles y el alta/desbloqueo por script tal como existen.
No requiere rol, endpoint ni migración nueva. Es la alternativa de menor cambio antes de presentar.

**Alternativa B: experimento mínimo de actor de plataforma.** Si German aprueba adelantar
parte de MVP 2, introducir un actor autenticado que únicamente pueda:

1. Dar de alta un local con su Gerente, reutilizando las reglas actuales.
2. Desbloquear un Gerente existente.
3. Quedar registrado como autor real de esas acciones en el local afectado.

El nombre/código del rol, permisos concretos y contratos HTTP **quedan pendientes de aprobación**.
El actor no recibiría automáticamente los permisos de Admin/Gerente, acceso a la operación,
lecturas de todos los locales, impersonación o suscripción a todos los topics.
La identidad del autor debe salir de la autenticación, nunca de un actorId enviado por el cliente.

**Alternativa C: versión completa del experimento delimitado.** Completar B con aprovisionamiento
seguro del primer operador, primer ingreso, recuperación/baja, autorización de ambas operaciones,
auditoría del actor y regresión integral. El criterio de completitud son esas dos capacidades existentes
y su ciclo de vida; no una consola global de negocio inventada. Antes de implementar hay que decidir
el mecanismo exacto de aprovisionamiento/recuperación y si se conserva el script con su marca de origen.

Una consola con listado, suspensión, eliminación, reportes globales u otras facultades sería
**un alcance adicional sin especificación encontrada**. No se puede proponer honestamente como
requisito confirmado ni completar sus endpoints/tablas con suposiciones.

### ¿Hace falta realmente otro rol?

Para MVP 1, **no se encontró necesidad de un rol adicional**: Admin, Gerente y script cubren
las responsabilidades implementadas. La referencia futura sí distingue una responsabilidad de
plataforma: atribuir y autorizar al operador de los comandos.

Si se aprueba B/C, un actor separado sería más coherente que ampliar globalmente GERENTE_DUENO,
porque conserva la diferencia entre propietario del restaurante y operador de plataforma.
Duplicaría responsabilidades si solo repite gestión de empleados/Admins, menú o reportes
que ya corresponden a los actores existentes.

No se recomienda añadirlo solamente para mejorar la presentación.

## 14. Archivos y cambios condicionales

**Esta ejecución solo crea este documento.** Para A no hay cambios de funcionalidad.
Para B/C, la siguiente lista es **PROPUESTA NO IMPLEMENTADA**, sujeta a definición de contratos:

| Área / archivo actual o nuevo propuesto | Cambio condicionado |
|---|---|
| Nueva migración Flyway | Datos de rol/permisos/asociaciones aprobados; sin editar las aplicadas |
| usuarios/dominio/Rol.java | Habilitar el método de login aprobado; SQL solo no basta |
| usuarios/dominio/Usuario.java | Creación explícita del nuevo tipo, sin reutilizar una factory de Gerente como si fuera el mismo rol |
| usuarios/aplicacion/AltaDeLocalService.java | Separar actor autenticado de Gerente afectado y conservar transacción/reglas |
| Nuevo caso de uso/adaptador HTTP de plataforma | Autorizar solamente las dos capacidades aprobadas; nombres y rutas pendientes |
| Aprovisionamiento operativo, posiblemente junto a ComandosDelLocal | Identidad inicial y recuperación segura; no contraseñas embebidas ni argumentos sensibles |
| Puertos/repositorios de usuarios, solo si el flujo aprobado los necesita | Búsquedas/validaciones específicas, sin acceso global implícito |
| Tests de autenticación, alta, desbloqueo, auditoría y autorización | Nuevo actor y conservación del aislamiento y roles existentes |
| README y docs/superadmin-presentacion.md | Contrato y demostración de lo que efectivamente se implemente |

JWT, cookies, CSRF y tablas de auditoría pueden reutilizarse; no se ha justificado cambiarlos.
LocalDeConsulta, LocalDelGerenteService y los filtros de repositorios actuales deben conservar su alcance.
Una vista global, si se pidiera más adelante, necesitaría una política y consultas separadas.

## 15. Riesgos y decisiones pendientes

- **Alcance temporal:** el único respaldo explícito sitúa Super Admin en MVP 2; confirmar si se lo
  adelanta como experimento o si se presenta el MVP 1 existente.
- **Facultades:** confirmar si se aprueban exactamente alta de local y desbloqueo de Gerente.
  Cualquier listado de locales o lectura global requiere una definición adicional.
- **Identidad:** quién crea al primer operador, cuántos pueden existir y quién puede darles de baja.
- **Recuperación:** el script actual solo desbloquea Gerentes; no definir un bypass automático
  para recuperar el actor nuevo ni eliminar RNF-07.
- **Auditoría:** corregir la atribución sin fingir que los registros históricos del script ya
  identifican al ejecutor. Definir explícitamente la convivencia del script.
- **Propiedad:** MVP 1 asume un local; la base admite varios y una ruta selecciona el primero.
  No resolver ese tema indirectamente mediante un rol superior.
- **Aislamiento:** no crear localId comodín, permisos universales, suplantación ni bypass de STOMP.
- **Presentación:** una etiqueta de frontend «SuperAdmin» no crea una política de servidor y no
  debe presentar capacidades propuestas como si ya estuvieran funcionando.
- **Integración:** mantener esta rama separada de la mitigación de login hasta decidir su integración.

### Coordinación con el frontend — PROPUESTA NO IMPLEMENTADA

German indicó que el experimento necesitaría una pantalla adicional en el frontend y que debe
mantenerse en una rama separada. Este repositorio corresponde al backend; no se creó una pantalla.

Para el alcance mínimo, la pantalla tendría que contemplar las dos operaciones delimitadas:
alta de local con los datos actuales del Gerente y desbloqueo por email de Gerente. No se deduce
de esto un dashboard global, listado de todos los locales o impersonación.

Antes de integrar esa pantalla deben acordarse los contratos efectivos del backend, las guardas
por permisos, el primer ingreso y los estados de validación, falta de sesión y acceso denegado.
Se conservarán las cookies y CSRF actuales; el frontend no elegirá el usuario autor de auditoría.
Los nombres de rutas y permisos nuevos siguen pendientes de la implementación que se apruebe.

## 16. Tests existentes revisados y validación posterior

Se localizaron/revisaron, entre otros:

| Clase o familia existente | Evidencia que contiene |
|---|---|
| AltaDeLocalServiceTest | Alta atómica, Gerente sin local, primer ingreso, validación, email único y auditoría |
| ComandosDelLocalTest | Perfil/comandos, entrada de contraseña y resultados sin filtrar credenciales |
| GestionarAdminsServiceTest / AdminsDelGerenteTest | Permisos, varios Admins, bajas/reactivación, desbloqueo, CSRF y pertenencia |
| LocalDelGerenteServiceTest / ReportesDelGerenteTest | Propiedad, localId opcional/ajeno, lecturas y rechazo de otros roles |
| ControlDePermisosTest / ReportesYAuditoriaTest | 401/403, permisos y consultas por local |
| EmpleadosSeguridadHttpTest / EmpleadosDeNegocioTest | Roles gestionables, operaciones y aislamiento de empleados |
| CartaDelAdminTest / MesasAdminTest | Gestión, permisos, validación y entidades del local |
| IngresoConEmailTest / IngresoConPinTest / PrimerIngresoTest | Credenciales, bloqueo y primer acceso |
| CierreDeSesionTest / RenovacionDeSesionTest | Logout y renovación/inactividad |
| PantallaTest / PantallaStompTest / PublicadorStompTest | Contrato público, tokens, destinos y publicación |

No se crearon tests de SuperAdmin ni se ejecutó la suite en este análisis documental.
La inspección no se presenta como una prueba de ejecución aprobada.
Si se implementa B/C, harán falta casos de permiso ausente, CSRF, credenciales provisorias,
bloqueados/inactivos, datos inválidos/inexistentes, conflictos, recuperación, actor real de auditoría,
ausencia de ampliación accidental de ámbito y regresión de Admin/Gerente.
La selección específica y `mvn verify` se ejecutarán sobre la implementación aprobada.

## 17. Punto de decisión

El documento recibido exige **«No empezar la implementación hasta que German apruebe explícitamente
la propuesta»**. Este análisis termina aquí, antes de crear funcionalidad.

La decisión concreta es: **A**, conservar el MVP actual; o aprobar el alcance acotado **B/C**
y resolver identidad inicial/recuperación, permisos y contratos antes de programar.
Ninguna de las ideas opcionales se convierte en requisito por aparecer en este informe.

## Anexo. Endpoints actuales que declaran permisos

Inventario estático de annotations del commit base; las restricciones de propiedad/local de servicios
descritas arriba se suman a estos permisos. Las rutas de autenticación y las públicas se describen
en la sección de seguridad y no forman parte de la tabla de permisos.

| Método y ruta actuales | Permiso declarado | Controller |
|---|---|---|
| `GET /api/v1/admin/auditoria` | `auditoria.ver` | `AuditoriaController` |
| `GET /api/v1/admin/categorias` | `menu.gestionar` | `CategoriasAdminController` |
| `POST /api/v1/admin/categorias` | `menu.gestionar` | `CategoriasAdminController` |
| `PUT /api/v1/admin/categorias/orden` | `menu.gestionar` | `CategoriasAdminController` |
| `PUT /api/v1/admin/categorias/{id}` | `menu.gestionar` | `CategoriasAdminController` |
| `DELETE /api/v1/admin/categorias/{id}` | `menu.gestionar` | `CategoriasAdminController` |
| `GET /api/v1/platos` | `plato.cambiar_disponibilidad` | `DisponibilidadController` |
| `PATCH /api/v1/platos/{id}/disponibilidad` | `plato.cambiar_disponibilidad` | `DisponibilidadController` |
| `GET /api/v1/admin/etiquetas` | `menu.gestionar` | `EtiquetasAdminController` |
| `POST /api/v1/admin/etiquetas` | `menu.gestionar` | `EtiquetasAdminController` |
| `PUT /api/v1/admin/etiquetas/{id}` | `menu.gestionar` | `EtiquetasAdminController` |
| `DELETE /api/v1/admin/etiquetas/{id}` | `menu.gestionar` | `EtiquetasAdminController` |
| `GET /api/v1/admin/platos` | `menu.gestionar` | `PlatosAdminController` |
| `POST /api/v1/admin/platos` | `menu.gestionar` | `PlatosAdminController` |
| `PUT /api/v1/admin/platos/{id}` | `menu.gestionar` | `PlatosAdminController` |
| `PATCH /api/v1/admin/platos/{id}/destacado` | `menu.gestionar` | `PlatosAdminController` |
| `GET /api/v1/alertas/mesas-sin-mozo` | `empleado.gestionar` **o** `pedido.aprobar_rechazar` | `AlertasController` |
| `GET /api/v1/caja/pedidos` | `pedido.aprobar_rechazar` | `CajaController` |
| `POST /api/v1/caja/pedidos/{id}/aprobar` | `pedido.aprobar_rechazar` | `CajaController` |
| `POST /api/v1/caja/pedidos/{id}/rechazar` | `pedido.aprobar_rechazar` | `CajaController` |
| `GET /api/v1/cocina/pedidos` | `pedido.cambiar_estado_cocina` | `CocinaController` |
| `POST /api/v1/cocina/pedidos/{id}/en-preparacion` | `pedido.cambiar_estado_cocina` | `CocinaController` |
| `POST /api/v1/cocina/pedidos/{id}/listo` | `pedido.cambiar_estado_cocina` | `CocinaController` |
| `GET /api/v1/caja/cuentas` | `cuenta.cerrar` | `CuentasController` |
| `POST /api/v1/caja/cuentas/{id}/cerrar` | `cuenta.cerrar` | `CuentasController` |
| `GET /api/v1/caja/cuentas/{id}/ticket` | `cuenta.cerrar` | `CuentasController` |
| `GET /api/v1/caja/pedidos/aprobados` | `pedido.priorizar` | `GestionDeColaController` |
| `PUT /api/v1/caja/pedidos/{id}/prioridad` | `pedido.priorizar` | `GestionDeColaController` |
| `POST /api/v1/caja/pedidos/{id}/cancelar` | `pedido.cancelar` | `GestionDeColaController` |
| `POST /api/v1/caja/pedidos/cancelar` | `pedido.cancelar` | `GestionDeColaController` |
| `GET /api/v1/admin/mesas` | `mesa.gestionar` | `MesasAdminController` |
| `GET /api/v1/admin/mesas/{id}` | `mesa.gestionar` | `MesasAdminController` |
| `POST /api/v1/admin/mesas` | `mesa.gestionar` | `MesasAdminController` |
| `PUT /api/v1/admin/mesas/{id}` | `mesa.gestionar` | `MesasAdminController` |
| `PATCH /api/v1/admin/mesas/{id}/inhabilitada` | `mesa.gestionar` | `MesasAdminController` |
| `DELETE /api/v1/admin/mesas/{id}` | `mesa.gestionar` | `MesasAdminController` |
| `GET /api/v1/admin/mesas/{id}/qr` | `mesa.gestionar` | `MesasAdminController` |
| `GET /api/v1/mozo/mesas` | `mesa.ver_asignadas` | `MozoController` |
| `GET /api/v1/mozo/avisos` | `aviso.atender` | `MozoController` |
| `POST /api/v1/mozo/avisos/{id}/atender` | `aviso.atender` | `MozoController` |
| `POST /api/v1/mozo/pedidos/{id}/servido` | `pedido.marcar_servido` | `MozoController` |
| `GET /api/v1/admin/pantalla` | `local.configurar` | `PantallaController` |
| `GET /api/v1/admin/reportes/ventas` | `reporte.ver` | `ReportesController` |
| `GET /api/v1/admin/reportes/tiempos-preparacion` | `reporte.ver` | `ReportesController` |
| `GET /api/v1/admin/reportes/platos-mas-pedidos` | `reporte.ver` | `ReportesController` |
| `GET /api/v1/gerente/admins` | `admin.gestionar` | `AdminsGerenteController` |
| `POST /api/v1/gerente/admins` | `admin.gestionar` | `AdminsGerenteController` |
| `PATCH /api/v1/gerente/admins/{id}/activo` | `admin.gestionar` | `AdminsGerenteController` |
| `POST /api/v1/gerente/admins/{id}/desbloquear` | `admin.gestionar` | `AdminsGerenteController` |
| `GET /api/v1/gerente/auditoria` | `auditoria.ver` | `AuditoriaGerenteController` |
| `GET /api/v1/admin/empleados` | `empleado.gestionar` | `EmpleadosAdminController` |
| `GET /api/v1/admin/empleados/{id}` | `empleado.gestionar` | `EmpleadosAdminController` |
| `POST /api/v1/admin/empleados` | `empleado.gestionar` | `EmpleadosAdminController` |
| `PUT /api/v1/admin/empleados/{id}` | `empleado.gestionar` | `EmpleadosAdminController` |
| `DELETE /api/v1/admin/empleados/{id}` | `empleado.gestionar` | `EmpleadosAdminController` |
| `POST /api/v1/admin/empleados/{id}/pin` | `empleado.gestionar` | `EmpleadosAdminController` |
| `PUT /api/v1/admin/empleados/{id}/turnos` | `empleado.gestionar` | `EmpleadosAdminController` |
| `PUT /api/v1/admin/empleados/{id}/mesas` | `empleado.gestionar` | `EmpleadosAdminController` |
| `GET /api/v1/admin/empleados/{id}/ausencias` | `empleado.gestionar` | `EmpleadosAdminController` |
| `POST /api/v1/admin/empleados/{id}/ausencias` | `empleado.gestionar` | `EmpleadosAdminController` |
| `PATCH /api/v1/admin/empleados/{id}/ausencias/{ausenciaId}` | `empleado.gestionar` | `EmpleadosAdminController` |
| `GET /api/v1/mozo/push/clave-publica` | `aviso.atender` | `PushController` |
| `POST /api/v1/mozo/push/suscripcion` | `aviso.atender` | `PushController` |
| `DELETE /api/v1/mozo/push/suscripcion` | `aviso.atender` | `PushController` |
| `GET /api/v1/gerente/reportes/ventas` | `reporte.ver` | `ReportesGerenteController` |
| `GET /api/v1/gerente/reportes/tiempos-preparacion` | `reporte.ver` | `ReportesGerenteController` |
| `GET /api/v1/gerente/reportes/platos-mas-pedidos` | `reporte.ver` | `ReportesGerenteController` |
