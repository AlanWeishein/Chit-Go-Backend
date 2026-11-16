# Prueba de seguridad: abuso del bloqueo de Admin

Fecha: **16 de noviembre de 2026**.

Este informe conserva la reproducción **anterior a la mitigación**. La corrección y los resultados
posteriores están en [Seguridad del login](seguridad-account-lockout.md).

**Resultado: PASÓ. El escenario de Account Lockout Abuse es reproducible en el entorno local.**
Un tercero que conoce el email de un Admin puede provocar su bloqueo mediante cinco contraseñas
incorrectas válidas en formato. Después, la contraseña correcta tampoco permite ingresar.
Esto confirma el comportamiento de RNF-07 y evidencia su posible abuso para denegar el acceso.

## Entorno y alcance

- Endpoint: `POST /api/v1/auth/admin/login`.
- Usuario seed: `admin@laesquina.test`.
- Perfil Spring: `dev`; MySQL 8.4 del Compose, en `localhost:3307/chitgo`.
- Test agregado: `cincoRequestsFallidasDesdeCeroPuedenBloquearAlAdmin`, en
  [IngresoConEmailTest.java](../src/test/java/com/chitgo/usuarios/infraestructura/web/IngresoConEmailTest.java).
- Se usa MockMvc con el contexto de Spring, filtros de seguridad, CSRF obtenido desde `/api/v1/ping`
  y persistencia real en MySQL. Cada intento atraviesa el endpoint; no se prepara el contador en cuatro.
- MockMvc simula las solicitudes HTTP dentro del proceso: este ensayo no usa un puerto HTTP real
  ni verifica un proxy, un WAF o infraestructura de producción.
- Solo se agregaron el test y este informe. El comportamiento productivo y RNF-07 se conservaron.

## Resultados comprobados por las aserciones

El contador inicial fue **0**. Todos los intentos respondieron con el detalle genérico
`Email o contraseña incorrectos`.

| Intento | Contraseña | HTTP | `intentos_fallidos` después | `Set-Cookie` |
|---|---|---|---|---|
| 1 | Incorrecta | 401 | 1 | Ausente |
| 2 | Incorrecta | 401 | 2 | Ausente |
| 3 | Incorrecta | 401 | 3 | Ausente |
| 4 | Incorrecta | 401 | 4 | Ausente |
| 5 | Incorrecta | 401 | 5 | Ausente |
| 6 | Correcta | 401 | 5 | Ausente |

Estado final del escenario, antes de la limpieza:

- `bloqueado_hasta` coincide con `Usuario.BLOQUEO_SIN_VENCIMIENTO`: **2038-01-01T00:00:00Z**.
  Es el valor centinela usado por el dominio para representar el bloqueo sin vencimiento automático.
- El sexto intento fue rechazado pese a usar la contraseña correcta.
- No se creó una cookie de sesión en ninguno de los seis POST: se verificó la ausencia de `Set-Cookie`.
  La cookie de CSRF obtenida previamente por el helper es parte de la preparación y no una sesión.
- No hubo respuestas **500** en los seis intentos.

El `@AfterEach` existente conserva la restauración de `intentos_fallidos = 0`,
`bloqueado_hasta = NULL` y `activo = TRUE`. La consulta al MySQL local después de la ejecución
confirmó esos valores para Admin y Gerente/Dueño.

## Rate limiting

No se encontró rate limiting por origen o ventana temporal en el código, las dependencias ni la
configuración del repositorio. Se revisaron la cadena de filtros de `SecurityConfig`,
`IngresarConEmailService`, el dominio `Usuario`, el POM y la configuración local.
La protección existente es el contador por cuenta y el bloqueo de RNF-07.
Ninguno de los seis intentos devolvió 429. La prueba no permite afirmar qué controles existen
fuera de esta aplicación en un despliegue externo.

## Ejecución y regresión

```powershell
docker compose up -d --wait
mvn -Ddebug=false '-Dtest=IngresoConEmailTest#cincoRequestsFallidasDesdeCeroPuedenBloquearAlAdmin' test
mvn -Ddebug=false '-Dtest=IngresoConEmailTest,AutenticacionControllerTest,PrimerIngresoTest' test
```

- Prueba focalizada: **1 ejecutada, 0 fallos, 0 errores, 0 omitidas**.
- Regresión de autenticación: **45 ejecutadas, 0 fallos, 0 errores, 0 omitidas**.
  Incluye el test nuevo, ingreso legítimo de Admin y Gerente/Dueño, sesión, validación,
  respuestas genéricas ante emails inexistentes y primer ingreso.
- Ambas ejecuciones finalizaron con `BUILD SUCCESS`.
- No se ejecutó la suite completa del proyecto.

Los informes de la última ejecución quedan en `target/surefire-reports/`.
Maven mostró un aviso HTTP 401 al consultar metadatos del repositorio de Flyway;
ese aviso corresponde al repositorio de dependencias y no a una solicitud del login.

## Hallazgo y evaluación pendiente

El bloqueo depende de intentos no autenticados dirigidos al email de la víctima. En este escenario,
cinco solicitudes bastan para impedir nuevos ingresos legítimos. Las respuestas mantienen el mensaje
genérico, sin informar al cliente si el email existe o si la cuenta está bloqueada; esto no elimina
el abuso contra un email ya conocido.

Se mantiene RNF-07. Queda para evaluación una mitigación por origen y ventana temporal, su interacción
con el bloqueo de cuenta y la recuperación del acceso legítimo. No se implementó una mitigación en
esta tarea. El test agregado registra el comportamiento actual; una mitigación futura deberá incorporar
pruebas que verifiquen la protección esperada y preserven las respuestas que evitan enumerar usuarios.
