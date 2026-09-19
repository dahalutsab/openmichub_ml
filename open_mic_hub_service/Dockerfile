# ---------------------------------------------------------------------------
# Stage 1 — build
#
# Lombok does not support JDK 24+, so the build is pinned to 21 regardless of
# what the host has installed.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer keyed on pom.xml alone, so a
# source-only change reuses it instead of re-downloading the world.
# Deliberately avoids BuildKit cache mounts so the build works on a stock
# Docker install with no buildx plugin.
COPY pom.xml .
RUN mvn -B dependency:go-offline -q

COPY src ./src
RUN mvn -B clean package -DskipTests -q

# Split the fat jar so dependencies, loader and application code become
# separate image layers.
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted


# ---------------------------------------------------------------------------
# Stage 2 — runtime
#
# JRE on Alpine: no Maven, no JDK, no source. Around a quarter of the build image.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache curl openssl bash

# Runs unprivileged. A container escape from the app should not land as root.
#
# The uid and gid are pinned rather than auto-assigned: the signing keys live on
# a volume with mode 600, so an id that shifts when the base image changes
# leaves the container unable to read its own keys.
RUN addgroup -S -g 1001 openmic \
 && adduser -S -u 1001 -G openmic -h /app openmic

WORKDIR /app

# Ordered least- to most-frequently changed, so rebuilds reuse the layers above.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# The sed strips carriage returns: a checkout made before .gitattributes pinned
# *.sh to LF can still carry CRLF on Windows, and a "bash\r" shebang does not run.
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN sed -i 's/\r$//' /usr/local/bin/entrypoint.sh \
 && chmod +x /usr/local/bin/entrypoint.sh \
 && mkdir -p /app/uploads /app/certs \
 && chown -R 1001:1001 /app

USER openmic

# Signing keys and uploaded media live on volumes, not in the image.
VOLUME ["/app/uploads", "/app/certs"]

ENV JWT_PRIVATE_KEY=file:/app/certs/private_key.pem \
    JWT_PUBLIC_KEY=file:/app/certs/public_key.pem \
    UPLOAD_DIR=/app/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8181

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8181/actuator/health || exit 1

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]