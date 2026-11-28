# Imagen de produccion del backend (2026-11-27, preparacion de pilotos).
# Los tests NO corren aqui: los corre el CI (mvn verify) en cada PR; la imagen
# se construye desde un commit que ya paso el CI.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
# Usuario sin privilegios (ASVS V13): el proceso no corre como root.
RUN useradd --system --uid 10001 mezzalia \
    && mkdir -p /data/uploads \
    && chown mezzalia /data/uploads
WORKDIR /app
COPY --from=build /src/target/mezzalia-backend-*.jar app.jar
USER mezzalia
ENV TZ=America/Bogota \
    UPLOADS_DIR=/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
