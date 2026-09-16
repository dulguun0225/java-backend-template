# The single deployable image. Multi-stage so `docker compose up --build` is self-contained. Tests are skipped in
# the image build: the wall runs in CI, and Testcontainers needs a Docker daemon this build does not have.
# No Java agent anywhere (ForbiddenFlagsTest greps this file). Pin both base images by digest in a real
# deployment; tags are used here so the template does not rot on a stale digest.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true -Dspotless.check.skip=true -Dlicense.skip=true -pl starter-app -am package

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/starter-app/target/starter-app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
