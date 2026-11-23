# Imagen del back de Chit&Go. La base y el front corren en sus propios contenedores (compose del repo de entrega).
# El perfil de Spring y la conexión a la base llegan por variables de entorno (SPRING_PROFILES_ACTIVE, DB_URL...).

# Etapa 1: compila el jar. Los tests no corren acá porque necesitan el MySQL del compose.
FROM maven:3.10.0-eclipse-temurin-21-noble AS build
WORKDIR /build

# Primero el pom solo: si cambia el código y no las dependencias, Docker reusa esta capa.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# Etapa 2: solo el JRE y el jar, sin Maven ni el código fuente.
FROM eclipse-temurin:21.0.13_9-jre-noble

RUN groupadd --system chitgo && useradd --system --gid chitgo --no-create-home chitgo

WORKDIR /app
COPY --from=build --chown=chitgo:chitgo /build/target/chit-go-back-*.jar app.jar

# Las fotos de los platos (ST-29): /app/imagenes es el ./imagenes por defecto de chitgo.imagenes.carpeta. Se crea
# acá, con dueño chitgo, porque al montar un volumen vacío Docker le copia la carpeta y su dueño; si no existe,
# el back no puede escribir y la primera foto falla con "Permission denied".
RUN mkdir /app/imagenes && chown chitgo:chitgo /app/imagenes

USER chitgo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
