# syntax=docker/dockerfile:1
#
# hist-db server image — multi-stage GraalVM native-image build.
#
#   builder : debian bookworm + GraalVM CE (the exact version pinned in .sdkmanrc) + sbt
#             (the version pinned in project/build.properties) → `sbt server/nativeImage`
#   runtime : debian bookworm-slim + the single `histdb-server` binary
#
# Both stages share the same glibc (bookworm), so the binary produced in the builder is guaranteed
# to run in the runtime stage. Everything is driven off TARGETARCH, so the same Dockerfile builds
# natively on arm64 (Apple Silicon) and amd64 — there are no arch-specific branches beyond picking
# the matching GraalVM tarball.
#
# Build:  docker build -t hist-db-server .
# Run:    docker run --rm -p 8000:8000 -e HISTDB_DB_URL=... hist-db-server
# Usually you want `docker compose up -d` instead — see README.md.

# GraalVM CE 22.0.2 — the JDK pinned in .sdkmanrc, and it must stay in sync with it.
# NOT 25: ox 0.5.x (pulled in by tapir-netty-server-sync) is compiled against JDK 22's preview
# StructuredTaskScope and dies at NettySyncServer.startAndWait on JDK 25 with
# `NoClassDefFoundError: ox.DoNothingScope`. Upgrading the JDK means bumping tapir/ox first
# (tracked in docs/plan.md wave 3).
ARG GRAALVM_VERSION=22.0.2
ARG SBT_VERSION=2.0.7
ARG HISTDB_PORT=8000

# ── builder ──────────────────────────────────────────────────────────────────────────────────────
FROM debian:bookworm-slim AS builder

ARG GRAALVM_VERSION
ARG SBT_VERSION
ARG TARGETARCH

# native-image links the final binary with the system toolchain: gcc + zlib headers are required.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      ca-certificates curl tar gzip build-essential zlib1g-dev \
 && rm -rf /var/lib/apt/lists/*

# GraalVM CE — same build as `.sdkmanrc` (java=22.0.2-graalce). Checksum-verified.
ENV GRAALVM_HOME=/opt/graalvm
RUN set -eux; \
    case "${TARGETARCH}" in \
      amd64) garch=x64 ;; \
      arm64) garch=aarch64 ;; \
      *) echo "unsupported TARGETARCH=${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    base="https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_VERSION}"; \
    file="graalvm-community-jdk-${GRAALVM_VERSION}_linux-${garch}_bin.tar.gz"; \
    curl -fsSL "${base}/${file}"        -o /tmp/graalvm.tgz; \
    curl -fsSL "${base}/${file}.sha256" -o /tmp/graalvm.sha256; \
    echo "$(cat /tmp/graalvm.sha256)  /tmp/graalvm.tgz" | sha256sum -c -; \
    mkdir -p "${GRAALVM_HOME}"; \
    tar -xzf /tmp/graalvm.tgz -C "${GRAALVM_HOME}" --strip-components=1; \
    rm -f /tmp/graalvm.tgz /tmp/graalvm.sha256; \
    "${GRAALVM_HOME}/bin/native-image" --version

ENV JAVA_HOME=${GRAALVM_HOME}
ENV PATH=${GRAALVM_HOME}/bin:${PATH}

# sbt — pure JVM, one tarball for every architecture.
RUN set -eux; \
    curl -fsSL "https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz" -o /tmp/sbt.tgz; \
    tar -xzf /tmp/sbt.tgz -C /opt; \
    rm -f /tmp/sbt.tgz
ENV PATH=/opt/sbt/bin:${PATH}

WORKDIR /build

# sbt's own JVM. native-image is the memory-hungry part and is configured separately below.
ENV SBT_OPTS="-Xmx2g -Dsbt.color=false -Dsbt.supershell=false"
# The `nativeImage` task in build.sbt shells out to `native-image` without -J flags; NATIVE_IMAGE_OPTIONS
# is how GraalVM lets you give the image builder its own heap. Raise this if the build is OOM-killed
# (symptom: "Image build request failed with exit status 137"); lower it for a small CI runner.
ENV NATIVE_IMAGE_OPTIONS="-J-Xmx8g"

# 1) Resolve dependencies in their own cached layer — a source-only change does not re-download.
COPY project/ project/
COPY build.sbt ./
RUN --mount=type=cache,target=/root/.cache/coursier \
    --mount=type=cache,target=/root/.sbt \
    sbt -batch update

# 2) Sources, then the native image itself (~1 min of native-image on top of the Scala compile).
#
# sbt 2 does NOT use the sbt-1 `modules/<m>/target/` layout — a module's target is
# `<root>/target/out/jvm/<scalaVersion>/<name>/`. Rather than encode that (it is an sbt
# implementation detail), find the produced executable and park it at a stable path.
COPY modules/ modules/
RUN --mount=type=cache,target=/root/.cache/coursier \
    --mount=type=cache,target=/root/.sbt \
    set -eux; \
    sbt -batch server/nativeImage; \
    bin="$(find /build/target /build/modules -type f -name histdb-server -perm -u+x -print -quit)"; \
    test -n "${bin}"; \
    mkdir -p /out; \
    install -m 0755 "${bin}" /out/histdb-server; \
    ls -lh /out/histdb-server

# ── runtime ──────────────────────────────────────────────────────────────────────────────────────
FROM debian:bookworm-slim AS runtime

ARG HISTDB_PORT

# curl is here for HEALTHCHECK only; ca-certificates + tzdata are what the binary needs at runtime.
#
# pg_dump/pg_restore are needed for `POST /api/backup` and `/api/restore` (plan §1.10): the server
# SPAWNS them, so they must be in this image, and their major version must MATCH the postgres service
# (compose pins postgis/postgis:16-3.4) — a newer pg_restore emits `SET transaction_timeout`, which
# Postgres < 17 rejects. Debian bookworm ships 15, so the client comes from the PGDG repo.
#
# Only libpq5 (+ the two compression libs pg_dump links) is INSTALLED; the two binaries are unpacked
# straight out of the postgresql-client-16 .deb, because installing that package would drag in
# postgresql-client-common → perl → ~50 MB of interpreter this image has no other use for.
# `pg_dump --version` below fails the build if that ever stops being enough.
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl tzdata gnupg; \
    curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc \
      | gpg --dearmor -o /usr/share/keyrings/pgdg.gpg; \
    echo "deb [signed-by=/usr/share/keyrings/pgdg.gpg] http://apt.postgresql.org/pub/repos/apt bookworm-pgdg main" \
      > /etc/apt/sources.list.d/pgdg.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends libpq5 libzstd1 liblz4-1; \
    cd /tmp; \
    apt-get download postgresql-client-16; \
    dpkg-deb --fsys-tarfile postgresql-client-16_*.deb \
      | tar -x -C / ./usr/lib/postgresql/16/bin/pg_dump ./usr/lib/postgresql/16/bin/pg_restore; \
    ln -s /usr/lib/postgresql/16/bin/pg_dump    /usr/local/bin/pg_dump; \
    ln -s /usr/lib/postgresql/16/bin/pg_restore /usr/local/bin/pg_restore; \
    rm -f /tmp/*.deb; \
    apt-get purge -y --auto-remove gnupg; \
    rm -rf /var/lib/apt/lists/*; \
    pg_dump --version; pg_restore --version; \
    useradd --system --uid 10001 --create-home --home-dir /app histdb

COPY --from=builder /out/histdb-server /usr/local/bin/histdb-server

# Defaults for a container: bind all interfaces INSIDE the container (compose publishes it to
# 127.0.0.1 only). Every one of these is overridden by compose / `docker run -e`.
ENV HISTDB_BIND=0.0.0.0 \
    HISTDB_PORT=${HISTDB_PORT} \
    HISTDB_CORPUS_DIR=/corpus \
    HISTDB_BACKUPS_DIR=/backups

# `histdb backup` writes here, and compose mounts the `backups` volume over it. Creating it in the
# image with the right owner is what makes a FRESH named volume writable by the unprivileged user:
# Docker initialises an empty volume from the image directory, ownership included.
RUN mkdir -p /backups && chown histdb:histdb /backups
VOLUME ["/backups"]

WORKDIR /app
USER histdb
EXPOSE ${HISTDB_PORT}

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
  CMD curl -fsS "http://127.0.0.1:${HISTDB_PORT:-8000}/api/health" || exit 1

ENTRYPOINT ["histdb-server"]
