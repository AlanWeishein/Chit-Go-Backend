# Pruebas de carga del Sprint 3

## Ejecutar

Requiere Java 21 y el MySQL del Compose saludable (`docker compose up -d --wait`).
No requiere abrir manualmente un backend: el test inicia uno en un puerto aleatorio.

```powershell
mvn -Pcarga test
# Aumentar la duración del tramo sostenido (1 a 30 minutos):
mvn -Pcarga '-Dcarga.minutos=5' test
```

El perfil ejecuta únicamente `CargaSprint3IT`; `mvn test` no incluye esta prueba lenta.
Los errores HTTP, de integridad, falta de eventos y p95 mayor que 2000 ms hacen fallar la ejecución.
El resumen se escribe en `target/carga-sprint3.json`, sin cookies, credenciales ni tokens.

## Escenario y medición

- Crea un local de prueba con 30 mesas y un plato. Usa el propietario de la seed como referencia,
  sin modificarlo. Los operadores Caja/Cocina son fixtures propios; sus JWT se emiten con el emisor
  real de la app. No se mide el login en este ensayo (RNF-05 está fuera del alcance).
- Abre las 30 cuentas mediante HTTP con cookies CSRF y sesiones de cliente independientes.
- RNF-01: una barrera dispara 30 envíos simultáneos. Comprueba 30 ids distintos, una cuenta
  por mesa, ausencia de cruces entre cuentas/sesiones/locales y la mesa de cada pedido.
- Conecta un KDS mediante WebSocket/STOMP real, con cookie de sesión de Cocina y suscripción al
  canal de su local. Caja aprueba los pedidos por HTTP; el test recibe los eventos y confirma que
  los pedidos aparecen en el snapshot del KDS.
- RNF-06: mantiene las 30 mesas activas y envía 10 pedidos por minuto (uno cada seis segundos)
  durante el tramo sostenido. Verifica cantidad final en MySQL y KDS.
- RNF-03: calcula el p95 por rango más próximo, por separado para ráfaga y tramo sostenido.
  Mide desde `ocurridoEn` del servidor al aprobar hasta la recepción del evento en el cliente
  WebSocket de prueba. Incluye el commit y el transporte; ambos relojes son del mismo equipo.
- Al terminar, incluso si falla una aserción, elimina solamente los datos del local de prueba.

## Alcance de la evidencia

Este ensayo usa loopback y MySQL en Docker. No mide el render del navegador, la TV ni la red Wi-Fi
del restaurante. El cumplimiento de RNF-03 en la red del local necesita repetir la medición allí
con el front/KDS. Una muestra de un minuto es una línea base local, no una prueba prolongada de
estabilidad. No se reemplaza esa validación por latencias REST ni se incluyen resultados inventados.

Primera ejecución (16/11/2026): 30 + 10 pedidos, cero pérdidas/cruces, p95 16,44 ms / 9,52 ms.
Resumen exportado: [resultados-carga-sprint3.json](resultados-carga-sprint3.json).
