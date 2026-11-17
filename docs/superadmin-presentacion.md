# SuperAdmin experimental: backend y prueba del prototipo

Rama: **feature/superadmin**. Base: **a7cb901**. Fecha: **16 de noviembre de 2026**.
German aprobó implementar y publicar el experimento mínimo después del
[análisis inicial](superadmin-analisis.md). No se integra automáticamente a develop.

## Qué resuelve

Las operaciones existentes por script creaban locales y desbloqueaban Gerentes sin identificar
al operador humano en la auditoría. El prototipo permite ejecutarlas desde una sesión autenticada
de plataforma y registrar a ese usuario como autor, en el local afectado.

Se agregó el rol **SUPERADMIN**, con `localId = null`, y exactamente dos permisos:

| Permiso | Operación |
|---|---|
| `local.crear` | Alta transaccional de local y su Gerente/Dueño |
| `gerente.desbloquear` | Levantar RNF-07 del Gerente de un local existente |

No hereda permisos de Admin, Gerente, Mozo, Caja o Cocina. No tiene listado global, reportes,
auditoría global, suspensión/eliminación de locales ni impersonación. Tampoco puede operar
los topics STOMP de los locales. El frontend aún necesita la pantalla adicional.

## Arquitectura y decisiones

- V10 agrega el rol y sus dos permisos. No se editaron migraciones aplicadas ni se agregaron tablas,
  columnas o cuentas con contraseñas precargadas.
- `Rol` habilita el login por email del operador. Se reutilizan BCrypt, JWT, cookies, CSRF,
  sesión sin local de diez minutos, primer ingreso, logout y bloqueo por cinco fallos.
- `SuperadminController` expone solo dos escrituras. Los permisos se verifican en controller y
  en los métodos de aplicación correspondientes; no se modificó `SecurityConfig`.
- `AltaDeLocalService` conserva la lógica y transacción del alta/desbloqueo. Sus nuevas entradas
  reciben el principal de la sesión, no un identificador de autor elegido por el frontend.
- El alta local/Gerente y su auditoría se confirman en la misma transacción. Email repetido devuelve 409.
  Las solicitudes concurrentes con igual email no generan locales o Gerentes duplicados.
- Las entradas antiguas del script se conservan con `origen: script` y su atribución anterior.
  No se reescribe el historial para fingir que se conocía al operador.
- `SuperadminOperativoService` permite alta y desbloqueo locales del operador, mediante el perfil
  `script`. Estas acciones no se exponen como endpoints.

## Preparación local aislada

Las pruebas se ejecutan en **chitgo_superadmin_test**, dentro del MySQL del Compose.
La base `chitgo` utilizada por otras ramas no recibe V10 durante estas pruebas.
Esto evita que el historial Flyway de develop quede afectado por la migración experimental.

1. Levantar MySQL: `docker compose up -d --wait`.
2. Crear la base aislada y otorgar acceso al usuario local configurado. Con los valores por defecto
   del Compose, el SQL administrativo es:

```sql
CREATE DATABASE IF NOT EXISTS chitgo_superadmin_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON chitgo_superadmin_test.* TO 'chitgo'@'%';
```

3. En cada terminal del backend o script, seleccionar esa base:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3307/chitgo_superadmin_test'
mvn clean spring-boot:run '-Dspring-boot.run.profiles=dev'
```

El arranque aplica las migraciones, incluida V10, y la seed de desarrollo. No hay SuperAdmin en la seed.
La compilación limpia evita reutilizar clases de otra rama, especialmente si se probó antes el rate limiter.
El perfil `script` requiere la base ya migrada; no ejecuta Flyway. Los cambios del archivo de seed
pueden borrar datos de prueba conforme al comportamiento existente del proyecto.

## Crear y desbloquear al operador

La persona con acceso operativo a la máquina/base crea la cuenta. No existe un endpoint público de
registro ni otro endpoint para crear SuperAdmins.

En otra terminal con el mismo `DB_URL`, cargar una contraseña provisoria sin escribirla en el historial:

```powershell
$env:CHITGO_CONTRASENA = [System.Net.NetworkCredential]::new('', (Read-Host 'Contraseña provisoria' -AsSecureString)).Password
try {
  mvn spring-boot:run '-Dspring-boot.run.profiles=script' '-Dspring-boot.run.arguments=crear-superadmin --nombre=Operador --email=superadmin@prototipo.test'
} finally {
  Remove-Item Env:CHITGO_CONTRASENA
}
```

El comando valida datos, email único y contraseña; guarda BCrypt y exige cambio en el primer ingreso.
No muestra la contraseña ni su hash. También puede leerla desde una consola interactiva si el proceso
dispone de ella; con Maven se recomienda el mecanismo de entorno anterior.

Si cinco fallos bloquean al operador:

```powershell
mvn spring-boot:run '-Dspring-boot.run.profiles=script' '-Dspring-boot.run.arguments=desbloquear-superadmin --email=superadmin@prototipo.test'
```

El comando solo levanta el bloqueo existente: no cambia contraseña, no elimina el cambio obligatorio
ni reactiva usuarios inactivos. No sirve para desbloquear usuarios de otros roles.
No se agregó recuperación de contraseña olvidada ni gestión HTTP de otras cuentas SuperAdmin.

Los comandos requieren proteger el acceso al sistema operativo y la base. Su ejecución no produce
una auditoría global de plataforma: el esquema actual exige un local por registro y el operador
no tiene uno. La auditoría con autor real implementada corresponde a las dos operaciones HTTP.

## Contratos para la nueva pantalla del frontend

La pantalla debe usar la sesión/cookies y CSRF existentes. La respuesta de sesión indica
`rol: SUPERADMIN`, `local: null` y `debeCambiarCredencial`. No incluye una lista de permisos.
El frontend puede usar el rol para navegar a esta pantalla; la autorización efectiva se verifica
por permisos en el backend.

### Login y primer ingreso

`POST /api/v1/auth/admin/login`:

```json
{"email":"superadmin@prototipo.test","password":"<contraseña elegida localmente>"}
```

Si `debeCambiarCredencial` es true, debe usar primero `POST /api/v1/auth/credencial`:

```json
{"password":"<nueva contraseña>","passwordRepetida":"<la misma nueva contraseña>"}
```

Debe ser distinta de la inicial. Hasta ese cambio, las operaciones de plataforma devuelven 403
con código `CREDENCIAL_PROVISORIA`. La sesión se consulta con `GET /api/v1/auth/sesion`.

### Formulario 1: crear local y Gerente

`POST /api/v1/superadmin/locales`:

```json
{
  "nombreLocal": "Local prototipo",
  "direccion": "Calle de prueba 1",
  "nombreGerente": "Gerente de prueba",
  "email": "gerente@prototipo.test",
  "password": "<contraseña provisoria del nuevo Gerente>"
}
```

`direccion` es opcional. Se conservan tamaños y validaciones de alta existentes.
Responde **201** con `{localId, nombreLocal, tokenStaff, gerenteId, email}`.
El token staff corresponde al nuevo local y debe tratarse como dato de acceso para configurar su QR.
No se devuelve la contraseña, su hash ni un token de sesión del Gerente.

El Gerente entra por el login habitual, cambia su contraseña y crea los Admins de su local mediante
el flujo existente. El SuperAdmin no adquiere esa sesión ni se convierte en propietario.

### Formulario 2: desbloquear Gerente

`POST /api/v1/superadmin/gerentes/desbloquear`:

```json
{"email":"gerente@prototipo.test"}
```

Responde **200** con uno de estos resultados:

```json
{"resultado":"DESBLOQUEADO"}
```

```json
{"resultado":"NO_ESTABA_BLOQUEADO"}
```

El segundo caso no escribe ni duplica auditoría. No requiere ni recibe localId.
El local se resuelve por propiedad del Gerente; se conserva la suposición actual de un local por Gerente.

### Errores y estados de UI

| HTTP | Tratamiento esperado |
|---|---|
| 400 | Datos o formato inválidos; mostrar ProblemDetail |
| 401 | Falta sesión, venció, credencial incorrecta o usuario inactivo según el endpoint |
| 403 | Falta CSRF, permiso o cambio inicial; distinguir `CREDENCIAL_PROVISORIA` cuando corresponda |
| 404 | En desbloqueo, email inexistente, otro rol o Gerente sin local: mismo detalle |
| 409 | Email ocupado al crear o Gerente inactivo al desbloquear |

Todo POST exige cookie `XSRF-TOKEN` y header `X-XSRF-TOKEN`. No enviar un autor de auditoría:
el backend toma la identidad del principal autenticado. No guardar secretos en logs o variables exportadas.

## Auditoría e aislamiento

- Alta: `CREAR_LOCAL`, entidad `LOCAL`, local afectado y usuario SuperAdmin real.
- Desbloqueo: `DESBLOQUEAR_GERENTE`, entidad `USUARIO`, Gerente afectado, su local y usuario SuperAdmin real.
- Los valores nuevos incluyen `origen: superadmin`; no incluyen contraseña, hash o token staff.
- Las lecturas y la retención por local existentes conservan su política. El SuperAdmin no recibe
  permiso para consultar auditoría global o la operación interna de los restaurantes.
- El token de pantalla y la autenticación STOMP no se modificaron.

## Prueba sin frontend

Importar [la colección Postman](postman/chitgo-superadmin.postman_collection.json).
Completar las contraseñas localmente y ejecutar en orden: obtener CSRF, login, cambio inicial si corresponde,
sesión y operaciones. En futuros logins usar la contraseña nueva. Postman conserva las cookies;
el script de la colección envía el CSRF vigente en las escrituras.

Los campos de contraseña vienen vacíos. No exportarlos completos ni usar credenciales reales de producción.
La colección no se ejecutó manualmente en Postman; sus operaciones se cubrieron en las pruebas HTTP.

## Validación automatizada

```powershell
mvn clean -Ddebug=false '-Dspring.datasource.url=jdbc:mysql://localhost:3307/chitgo_superadmin_test' '-Dtest=SuperadminTest,ComandosDelLocalTest,AltaDeLocalServiceTest,AdminsDelGerenteTest,ReportesDelGerenteTest,IngresoConEmailTest,PrimerIngresoTest,PermisosPorRolAdapterTest,GestionarEmpleadosServiceTest' test
mvn -Ddebug=false '-Dspring.datasource.url=jdbc:mysql://localhost:3307/chitgo_superadmin_test' verify
```

- Selección final: **136 pruebas, 0 fallos, 0 errores y 0 omitidas**.
- `SuperadminTest`: **19 ejecuciones**, con login/primer ingreso, autoría real, alta concurrente,
  Gerente/Admin funcionales, aislamiento, permisos, CSRF, conflictos y validaciones, inactivos,
  RNF-07, recuperación local, vencimiento/logout y ausencia de acceso STOMP a locales.
- `ComandosDelLocalTest`: **9 pruebas**, incluidas tres nuevas para el operador.
- Se amplió la prueba de permisos y se verificó que Admin no puede crear empleados de rol SuperAdmin.
- El pool de pruebas se limita a cinco conexiones por contexto y una mínima; no cambia el pool productivo.
- Suite completa: **967 pruebas en 95 clases, 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESS**.
  `mvn verify` generó el JAR ejecutable contra MySQL 8.4 en la base aislada.
- Se ejecutaron además `crear-superadmin` y `desbloquear-superadmin` con el JAR real y perfil script:
  ambos terminaron con código 0. La cuenta temporal se bloqueó para la comprobación, quedó en cero
  fallos y se eliminó al terminar; la contraseña se generó al azar y no se publicó.
- La comprobación final de Flyway dejó la base principal en V9 y la experimental en V10.

Las solicitudes HTTP se prueban con MockMvc y persistencia real. El control STOMP del nuevo rol se
comprueba con el interceptor real; no se afirma que esta tarea haya ejecutado una auditoría de protocolo
ni pruebas WebSocket completas por red. Logs locales: `target/pruebas-superadmin.log` y
`target/verificacion-superadmin.log`.

## Limitaciones del experimento

Sigue siendo una rama experimental. Conserva RNF-07 y el login existente; no incorpora automáticamente
el rate limiter que sigue en otra rama. No resuelve ataques lentos/distribuidos ni cambia la recuperación
de otros actores. Los comandos antiguos conservan su atribución histórica por origen script.
No se agregó MFA, rotación de tokens de pantalla, gestión completa del ciclo de vida del operador
ni pantallas de frontend.

## Demostración sugerida

1. Preparar el operador mediante el comando local y entrar desde Postman.
2. Mostrar el cambio obligatorio de contraseña.
3. Crear un local y abrir sesión con el nuevo Gerente para completar su flujo habitual.
4. Provocar cinco fallos de ese Gerente en el entorno de prueba y desbloquearlo desde la sesión SuperAdmin.
5. Consultar la auditoría como Gerente del local para mostrar al operador como autor.
6. Mostrar el rechazo de un Admin en las rutas SuperAdmin y del SuperAdmin en rutas operativas.

## Explicación para presentar (30–60 segundos)

El prototipo agrega un operador de plataforma para dos funciones que antes se ejecutaban por script:
crear un local con su Gerente y desbloquear a un Gerente. Reutiliza autenticación por email, primer
ingreso, cookies, CSRF y bloqueo por fallos. Tiene permisos específicos y cada operación registra
al operador real en la auditoría del local afectado. No hereda acceso a menús, empleados, reportes
ni canales de otros locales. El backend se puede probar con Postman; la pantalla del frontend queda
como integración posterior. Es un experimento separado del MVP existente.

## Preguntas para la presentación

- **¿Por qué otro rol?** Distingue al operador de plataforma del propietario del restaurante.
- **¿Puede leer todos los restaurantes?** No; solo tiene los dos permisos definidos.
- **¿Quién crea al primer operador?** La persona con acceso operativo usa el comando local y elige su contraseña.
- **¿Qué pasa si se bloquea?** Se usa el comando de desbloqueo; RNF-07 permanece activo.
- **¿Qué migración hizo falta?** V10 agrega datos de rol/permisos, sin usuarios ni nuevas tablas.
- **¿Ya está la pantalla?** No; se entregan contratos y colección para su integración.
