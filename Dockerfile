# The single deployable image. Multi-stage so `docker compose up --build` is self-contained. Tests are skipped in
# the image build: the wall runs in CI, and Testcontainers needs a Docker daemon this build does not have.
# Build context is backend/ (compose.yaml). No Java agent anywhere (ForbiddenFlagsTest greps this file). Pin both base images by digest in a real
# deployment; tags are used here so the template does not rot on a stale digest.
#
# JDK is BellSoft Liberica, the distribution Spring recommends, same vendor as mise.toml and ci.yml. No official
# maven image ships Liberica, so the build stage installs the Maven version mise.toml pins from the Apache archive
# and checks its published SHA-512.
FROM bellsoft/liberica-runtime-container:jdk-25-slim-glibc AS build
ARG MAVEN_VERSION=3.9.16
ARG MAVEN_SHA512=831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6
RUN wget -q -O /tmp/maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
 && echo "${MAVEN_SHA512}  /tmp/maven.tar.gz" | sha512sum -c - \
 && mkdir -p /opt/maven \
 && tar -xzf /tmp/maven.tar.gz -C /opt/maven --strip-components=1 \
 && rm /tmp/maven.tar.gz
ENV PATH="/opt/maven/bin:${PATH}"
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true -Dspotless.check.skip=true -Dlicense.skip=true package

FROM bellsoft/liberica-runtime-container:jre-25-slim-glibc AS runtime
WORKDIR /app
COPY --from=build /build/target/starter-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
