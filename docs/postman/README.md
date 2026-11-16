# Colección de Sprint 3 y ST-22

Importar `chit-go-sprint-3.postman_collection.json` en Postman. Son 33 operaciones:
sesión, mesas/QR, las 11 de empleados, reportes/auditoría de Admin y Gerente, y pantalla pública.

1. Configurar `baseUrl` y completar `email`/`password` como valores locales de Postman.
2. Ejecutar «Ping e inicializar CSRF» y luego «Login Admin o Gerente». El cookie jar guarda la sesión.
   Cada escritura toma la cookie XSRF-TOKEN vigente y la envía en X-XSRF-TOKEN automáticamente.
3. Elegir las operaciones necesarias. Para mesas, crear la demo guarda `mesaId`; para empleados,
   completar `pinProvisorio`, `passwordMozo` y un `usuarioMozo` único antes del alta, que guarda
   `empleadoId`. Crear una ausencia guarda `ausenciaId`. Las fechas son inclusivas.
4. Para Gerente/Dueño, cerrar sesión, entrar con su email y completar `localId` de su local.
5. Para la pantalla, entrar como Admin y usar «Acceso desde el Admin»: guarda `tokenPantalla` y
   `localId`. El snapshot público se puede consultar sin sesión. El contrato STOMP está en
   [sprint-3-german.md](../sprint-3-german.md).

Las carpetas agrupan operaciones para uso manual, no un Runner secuencial: dar de baja, reemplazar
mesas/turnos y cargar ausencias cambian el estado. Usar las altas demo para los ensayos. La baja de una
mesa exige cuenta libre; conserva su número reservado. «Reemplazar mesas» y «Reemplazar turnos» envían
la lista completa, no agregan una fila a las actuales.

El JSON y los cuerpos interpolados se validaron localmente. No se ejecutó la colección en Postman;
los endpoints se probaron mediante los tests HTTP de MySQL y el recorrido integrado. No se incluyen
credenciales reales; completar variables localmente y evitar exportarlas con secretos.
