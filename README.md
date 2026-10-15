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
   (por defecto `jdbc:mysql://localhost:3307/chitgo`, usuario y clave `chitgo`).
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

## Ramas

`main` estable, `develop` en desarrollo, `feature/...` por tarea. PR a `develop`, lo revisa otro.

## Pendiente

- `SecurityConfig` es temporal (permite todo). Reemplazar por JWT en cookie HttpOnly, permisos y CSRF.
