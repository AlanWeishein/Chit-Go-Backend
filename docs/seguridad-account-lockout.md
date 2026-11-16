# Seguridad del login: mitigación de Account Lockout Abuse

Fecha: **16 de noviembre de 2026**. Rama: **fix/account-lockout-abuse**.

## Hallazgo y alcance

El [ensayo original](prueba-seguridad-bloqueo-admin-2026-11-16.md) confirmó que un tercero que conoce
el email de un Admin puede bloquearlo con cinco contraseñas incorrectas, y que luego la contraseña
correcta también devuelve 401. El bloqueo de RNF-07 funcionaba conforme al diseño, pero admitía
una denegación de acceso contra cuentas conocidas. La reproducción original y 45 tests de autenticación
pasaron antes de la corrección.

La mitigación agrega una capa antes de autenticar para **Admin y Gerente/Dueño** en
`POST /api/v1/auth/admin/login`. RNF-07, su umbral y su bloqueo sin vencimiento automático se conservan.
Se mantienen CSRF, JWT, HttpOnly, SameSite, Secure y las respuestas genéricas de credenciales.
El login por PIN y el login de mozo conservan su comportamiento.

## Arquitectura revisada

Java 21, Spring Boot 4.0.8, monolito modular con separación de aplicación e infraestructura.
`SecurityConfig` exige CSRF; el controller recibe `IngresoConEmailDto` con `@Valid`.
`IngresarConEmailService` consulta el usuario con bloqueo pesimista y confirma los fallos mediante
`noRollbackFor`. `Usuario` registra el bloqueo al alcanzar el umbral configurado, que sigue siendo cinco.
`ErroresDeIngreso` maneja errores de autenticación; `ManejadorDeErrores` conserva validación y CSRF.
El puerto `Reloj` permite mover el tiempo en las pruebas sin esperar.

No se encontraron rate limiter, Redis, gateway, WAF, reverse proxy ni una política de proxies
confiables documentada en el repositorio. El Compose contiene MySQL y el README ejecuta el backend
directamente. No se hicieron suposiciones sobre infraestructura externa desconocida.

## Decisión sobre el límite global por IP

**Se implementó solamente el límite compuesto: 3 solicitudes por origen y email en 60 segundos.
No hay un límite global de solicitudes por IP.**

Esta decisión se documentó antes de finalizar la política, después de que German aprobara la
implementación y rechazara el cupo agregado de cuatro por IP. No se sustituyó ese cupo por otro
número mayor sin mediciones de uso que lo justifiquen.

Motivos y evidencia:

- El escenario académico confirmado es una ráfaga contra una misma cuenta desde un origen.
  Un límite menor que cinco interviene antes de RNF-07 cuando el contador empieza en cero.
- Las variantes de email que MySQL considera equivalentes comparten clave, resolviendo el motivo
  que originalmente había llevado a proponer un cupo adicional por IP.
- El test de NAT admite **30 solicitudes de 10 cuentas en la misma ventana y desde una sola IP**:
  tres contra Admin, tres ingresos correctos del Gerente y tres por cada uno de ocho emails inexistentes.
  Agotar Admin no agota el cupo del Gerente ni de las otras cuentas.
- Las pruebas de concurrencia impiden sobrepasar tres admisiones para la misma combinación.
- Un límite por IP de 20 o 30, por sí mismo, no detendría cinco variantes de una misma cuenta.
  Tampoco hay datos de carga de login que permitan elegir responsablemente un cupo compartido.

El límite compuesto es suficiente **para el escenario delimitado y comprobado**, no para toda forma
de abuso del endpoint. Una defensa futura contra volumen agregado, rotación masiva de cuentas o
presión sobre MySQL requerirá mediciones y una política adicional, idealmente en infraestructura
con la topología y los proxies confiables definidos.

### Alternativas evaluadas

| Alternativa | Evaluación |
|---|---|
| Solo IP | Mezcla a los operadores de una NAT; no se eligió como política de solicitudes. |
| Solo cuenta, compartida entre orígenes | Permite agotar el cupo de la víctima desde otra red y trasladar la denegación al limiter. |
| Origen + cuenta | Elegida para frenar la ráfaga sin acoplar cuentas diferentes bajo NAT. |
| Compuesto + límite global por IP | El cupo de cuatro fue rechazado. No se necesita para la prueba si las equivalencias comparten clave; un cupo mayor queda pendiente de evidencia de tráfico. |
| Proxy / gateway / WAF | No hay uno documentado; no se atribuye una protección inexistente. |
| Redis o persistencia de cupos | No se justifica para el alcance de una instancia local. |
| CAPTCHA, MFA o cambios de recuperación | Cambian el flujo y requieren otra decisión de requisitos. |

OWASP advierte sobre el abuso del bloqueo como denegación de acceso y el equilibrio entre seguridad
y usabilidad. No prescribe los números de esta implementación:
[Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#account-lockout).

## Política implementada

Recorrido: **CSRF → parseo y validación → clave de email → rate limiter → autenticación → RNF-07 → sesión**.

- Ventana móvil de **60 segundos**, con hasta **3 solicitudes admitidas** por origen y email equivalente.
- Cuenta solicitudes válidas, incluidas las exitosas, sin consultar existencia o resultado para reservar cupo.
- Dos errores todavía permiten un tercer intento correcto. Si los tres fallan, el cuarto debe esperar
  aunque lleve la contraseña correcta.
- Reserva atómica y comprobación bajo exclusión mutua antes de entrar al servicio.
- Los rechazos no consumen cupo ni extienden la espera. Un éxito no vacía el cupo del limiter.
- El origen proviene de `HttpServletRequest.getRemoteAddr()`. No se leen headers de proxy del cliente.
  `server.forward-headers-strategy: none` mantiene la dirección de la conexión en el despliegue directo.
- El limiter es exclusivo de este endpoint; PIN, mozo y otros módulos no usan este cupo.
- Datos inválidos siguen dando 400 y CSRF ausente o incorrecto sigue dando 403, incluso con el cupo agotado.

Respuesta limitada: **429 Too Many Requests**, `application/problem+json`, con el detalle
`Demasiadas solicitudes de ingreso. Intentá de nuevo más tarde.`.
`Retry-After` expresa la espera hasta liberar cupo, redondeada hacia arriba en segundos.
Es una estimación del estado observado; no reserva un turno frente a solicitudes concurrentes.

Una solicitud limitada no llama al servicio de ingreso, no incrementa `intentos_fallidos` ni crea
cookie de sesión. El 401 de credenciales conserva su cuerpo genérico. Las secuencias de 401 y 429,
sus cuerpos y Retry-After se prueban iguales para emails existentes e inexistentes.

### Equivalencias de email y coste

`usuario.email` usa `utf8mb4_0900_ai_ci`: convertir a minúsculas no cubre todas sus equivalencias.
Se obtiene una clave SHA-256 a partir de los pesos de esa colación mediante una consulta escalar
con parámetro enlazado. No consulta tablas ni identifica usuarios; también se aplica a emails inexistentes.
No se registran emails, orígenes, claves, contraseñas, JWT, cookies ni CSRF en logs nuevos.

Esto agrega un viaje a MySQL por solicitud válida, incluso si termina limitada. Protege antes de
BCrypt, de la consulta de usuario y de la transacción de autenticación; no protege ese cálculo escalar
ni es una defensa general contra saturar MySQL.

`WEIGHT_STRING` es una función documentada para uso interno y su comportamiento puede cambiar entre
versiones de MySQL. Sus claves aquí son efímeras, se recalculan en el servidor actual y no se persisten
ni se comparan entre versiones. Al actualizar MySQL o cambiar la colación de email se deben revisar
la consulta y las pruebas de equivalencia.
Referencia: [MySQL, WEIGHT_STRING](https://dev.mysql.com/doc/refman/8.4/en/string-functions.html#function_weight-string).

### Memoria y configuración

Sin Redis ni dependencias nuevas. Cada clave mantiene hasta tres timestamps; un mapa ordenado por
última admisión permite purgar claves vencidas. No se expulsan claves activas para admitir nuevas,
porque un atacante podría usar esa expulsión para reiniciar cupos.

| Propiedad bajo `chitgo.seguridad.login-email` | Valor por defecto |
|---|---|
| `solicitudes` | 3 |
| `ventana` | 60s |
| `maximo-de-claves` | 10000 |

Los valores deben ser positivos. El cupo debe ser menor que `chitgo.reglas.intentos-para-bloquear`;
una combinación incompatible impide arrancar. Son parámetros de seguridad técnica, separados
de las reglas del dominio. El tope de 10.000 claves es un presupuesto inicial de memoria, no una
capacidad demostrada mediante un ensayo de carga.

Cuando se alcanza el máximo de claves, las combinaciones nuevas reciben 429 hasta liberar una
clave por expiración. Retry-After se calcula a partir de esa expiración. Las claves existentes
conservan sus lugares restantes. Es un tope global de memoria, **no un cupo de solicitudes por IP**.
La saturación puede afectar a combinaciones legítimas nuevas: se documenta como limitación.

## Evidencia antes y después

Escenario: contador inicialmente cero, misma cuenta y origen, solicitudes dentro de 60 segundos.

| Solicitud | Antes: HTTP / fallos | Después: HTTP / fallos |
|---|---|---|
| 1, incorrecta | 401 / 1 | 401 / 1 |
| 2, incorrecta | 401 / 2 | 401 / 2 |
| 3, incorrecta | 401 / 3 | 401 / 3 |
| 4, incorrecta | 401 / 4 | 429 / 3 |
| 5, incorrecta | 401 / 5 | 429 / 3 |
| 6, correcta | 401 / 5 | 429 / 3 |
| Correcta después de vencer el cupo | 401 / 5, cuenta bloqueada | 200 / 0, sesión válida |

Antes: `bloqueado_hasta = 2038-01-01T00:00:00Z`.
Después de la ráfaga: `bloqueado_hasta = NULL`, tanto para Admin como para Gerente.
No hubo respuestas 500 ni cookies de sesión en las solicitudes limitadas comprobadas.

La regresión independiente de RNF-07 conserva los cinco fallos admitidos, avanzando el reloj
60 segundos antes del cuarto. En ese caso el quinto sí bloquea y la correcta devuelve 401.
Se conserva el nombre del test histórico `cincoRequestsFallidasDesdeCeroPuedenBloquearAlAdmin`
y se explica en su código el cambio de tiempo; también se conserva el test de quinto intento.
La prueba equivalente del Gerente se espacia de la misma forma.

## Pruebas y ejecución

Tests nuevos:

- `LimitadorDeLoginPorEmailTest`: 10 casos de ventana móvil, borde exacto, redondeo, concurrencia
  (100 solicitudes → 3 admisiones), aislamiento, memoria, expiración, reloj y configuración.
- `LimiteDeLoginPorEmailTest`: 17 ejecuciones con MockMvc y MySQL, incluidos Admin/Gerente,
  ráfaga, recuperación, cuentas/orígenes independientes, NAT, anti-enumeración, equivalencias,
  headers falsificados, CSRF/validación, logins exitosos, memoria llena y concurrencia.
  Para cada rol, 12 POST concurrentes producen 3 respuestas 401 y 9 respuestas 429.
- En memoria llena, la prueba usa un máximo reducido a 32 claves, comprueba el 429 sin sesión
  ni aumento de fallos y verifica recuperación al vencer los 60 segundos.

Cambios de tests existentes: reloj controlable y separación temporal de los ensayos de cinco
fallos en `IngresoConEmailTest`. `ReglasDesdeElYmlTest` configura un cupo de dos porque ese caso
reduce RNF-07 a tres. Un listener de tests limpia los cupos únicamente entre métodos que reutilizan
contextos Spring; no deshabilita la protección ni la reinicia entre solicitudes del mismo ensayo.
El helper de `BlanqueoYAusenciaEnElLoginTest` reutiliza una sesión de Admin dentro de cada caso,
en vez de autenticar de nuevo por cada operación. El pool del perfil de pruebas se limita a cinco
conexiones por contexto y una ociosa mínima para no agotar el MySQL local al conservar varios contextos.

Comandos:

```powershell
docker compose up -d --wait
mvn -Ddebug=false '-Dtest=LimitadorDeLoginPorEmailTest,LimiteDeLoginPorEmailTest,IngresoConEmailTest,AutenticacionControllerTest,PrimerIngresoTest,ReglasDesdeElYmlTest,IngresoConPinTest,IngresoDeMozoTest,BlanqueoYAusenciaEnElLoginTest,RenovacionDeSesionTest' test
mvn -Ddebug=false verify
```

Selección final de autenticación y seguridad: **141 pruebas, 0 fallos, 0 errores y 0 omitidas**.
Las selecciones anteriores de 71 y 113 también pasaron; luego se amplió la regresión.
El primer `verify` ejecutó 918 pruebas con **1 fallo, 16 errores y 0 omitidas**: el helper de
blanqueo agotaba el cupo al autenticar cuatro veces en un mismo caso; dos contextos no pudieron
arrancar por `Too many connections`. Se corrigieron el helper y el pool de pruebas descritos arriba.
El log original se conserva en `target/verificacion-account-lockout-intento1.log`.
**Verificación completa final: 918 pruebas en 90 clases, 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESS.**
`mvn -Ddebug=false verify` terminó con empaquetado del JAR ejecutable. Se conservó el pool productivo;
los ajustes de conexiones y de reutilización de sesión se limitaron a las pruebas.
La consulta final de MySQL confirmó Admin y Gerente con cero fallos, `bloqueado_hasta = NULL` y activos.

Logs locales: `target/pruebas-seguridad-autenticacion.log`,
`target/verificacion-account-lockout.log` e informes de Surefire en `target/surefire-reports/`.
Se usa MySQL local en `localhost:3307/chitgo`, perfil dev. MockMvc simula HTTP dentro del proceso;
no se ensayó un proxy, puerto HTTP real, WAF ni despliegue distribuido.

## Archivos de la corrección

Rutas de Java relativas a `src/main/java/com/chitgo/`:

| Archivo | Cambio |
|---|---|
| `usuarios/infraestructura/seguridad/PropiedadesDeLimiteDeLogin.java` | Configuración validada |
| `usuarios/infraestructura/seguridad/ConfiguracionDeLimiteDeLogin.java` | Registro de propiedades |
| `usuarios/infraestructura/seguridad/ClaveDeEmailParaLimite.java` | Clave efímera independiente de existencia |
| `usuarios/infraestructura/seguridad/LimiteDeLoginExcedidoException.java` | Rechazo y espera |
| `usuarios/infraestructura/seguridad/LimitadorDeLoginPorEmail.java` | Reserva atómica y memoria acotada |
| `usuarios/infraestructura/web/AutenticacionController.java` | Integración antes del servicio |
| `usuarios/infraestructura/web/ErroresDeIngreso.java` | 429 ProblemDetail y Retry-After |
| `src/main/resources/application.yml` | Valores y política de headers |
| `src/test/java/com/chitgo/usuarios/infraestructura/seguridad/LimitadorDeLoginPorEmailTest.java` | Tests unitarios nuevos |
| `src/test/java/com/chitgo/usuarios/infraestructura/seguridad/RestaurarLimitesDeLogin.java` | Aislamiento entre tests |
| `src/test/resources/META-INF/spring.factories` | Registro del listener de tests |
| `src/test/java/com/chitgo/usuarios/infraestructura/web/LimiteDeLoginPorEmailTest.java` | Tests de integración nuevos |
| `src/test/java/com/chitgo/usuarios/infraestructura/web/IngresoConEmailTest.java` | Regresión histórica conservada |
| `src/test/java/com/chitgo/usuarios/infraestructura/web/ReglasDesdeElYmlTest.java` | Configuración coherente con RNF-07 personalizado |
| `src/test/java/com/chitgo/usuarios/infraestructura/web/BlanqueoYAusenciaEnElLoginTest.java` | Reutiliza sesión en el helper |
| `src/test/resources/config/application.yml` | Pool acotado por contexto de pruebas |
| `README.md` | Contrato y parámetros |
| `docs/prueba-seguridad-bloqueo-admin-2026-11-16.md` | Enlace de la evidencia histórica a la corrección |
| `docs/seguridad-account-lockout.md` | Decisión, evidencia y limitaciones |

POM, migraciones, dominio, servicio de ingreso, JWT y cookies se conservaron.
La rama de revisión es `fix/account-lockout-abuse`; su publicación fue autorizada por German después
de validar la implementación. No se integró a `develop`. El resumen previo se conservó sin editar.

## Límites de la mitigación

- **Reduce ráfagas; no elimina el Account Lockout Abuse.** Un atacante paciente o distribuido todavía
  puede acumular cinco fallos admitidos y provocar RNF-07. No se cambia esa regla ni la recuperación.
- Un usuario con cuatro fallos previos todavía puede bloquearse con un solo fallo admitido.
- Operadores que comparten IP y usan la misma cuenta comparten cupo. Cuentas distintas no lo comparten.
- Los cupos se pierden al reiniciar y no se coordinan entre instancias.
- Un proxy sin política confiable haría compartir origen a sus clientes; requiere revisar el despliegue.
- La capacidad de claves y la consulta escalar tienen sus propios límites de recursos, descritos arriba.
- No se agregaron defensas para PIN, mozo ni parseo de cuerpos inválidos o enormes.

## Explicación para presentar (30–60 segundos)

Durante las pruebas detectamos que cinco intentos incorrectos podían bloquear una cuenta conocida.
El bloqueo cumplía RNF-07, pero podía usarse para impedir el ingreso del usuario legítimo.
Agregamos una protección de tres solicitudes por origen y email en un minuto: la siguiente recibe
429 y no aumenta el contador ni crea una sesión. También agrupamos variantes del email para que no
permitan eludir el cupo. Conservamos el bloqueo de cinco fallos cuando los intentos están permitidos.
Las cuentas distintas tienen cupos independientes aunque compartan la IP del restaurante.
La protección es local, en memoria, y reduce ráfagas; no elimina ataques lentos ni distribuidos.
