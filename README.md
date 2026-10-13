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

## Ramas

`main` estable, `develop` en desarrollo, `feature/...` por tarea. PR a `develop`, lo revisa otro.

## Pendiente

- `SecurityConfig` es temporal (permite todo). Reemplazar por JWT en cookie HttpOnly, permisos y CSRF.
- Migraciones Flyway en `db/migration` y datos de prueba en `db/dev` (todavía vacíos).
