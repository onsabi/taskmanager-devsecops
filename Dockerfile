# ---------- Etape 1 : build Maven ----------
# Image officielle Eclipse Temurin (JDK 21) avec Maven. Pour la prod, epingler par digest (sha256).
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Mise en cache des dependances : le pom est copie avant le code source
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Etape 2 : image d'execution minimale ----------
# JRE uniquement (pas de JDK, pas de Maven) pour reduire la surface d'attaque
FROM eclipse-temurin:21-jre-alpine AS runtime

# Utilisateur non-root dedie (UID/GID fixes)
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/taskmanager-1.0.0.jar app.jar

USER 10001:10001
EXPOSE 8080

# Options JVM adaptees aux conteneurs (respect des limites memoire Kubernetes)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
