// hist-db — containerized Scala MCP server + host CLI over a Postgres/PostGIS store.
// Toolchain pinned in .sdkmanrc (GraalVM CE 25 + native-image, sbt 2.0.7) and project/build.properties.

import Dependencies.*

ThisBuild / scalaVersion := "3.8.0"
ThisBuild / organization := "com.kubuszok"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
  "-source:future"
)

// Dependency versions live in project/Dependencies.scala (imported above as `V`).

// ── native-image (custom task; no sbt-2.0 plugin available yet) ─────────────────────────────────
// Shells out to GraalVM `native-image` over the module's full classpath. Reachability metadata
// (captured via the tracing agent) lives in each module's src/main/resources/META-INF/native-image/.
val nativeImage = taskKey[Unit]("Build a GraalVM native image for this module")

def nativeImageSettings(mainCls: String, outName: String): Seq[Setting[?]] = Seq(
  Compile / mainClass := Some(mainCls),
  nativeImage := {
    val log  = streams.value.log
    val conv = fileConverter.value // sbt 2.0: classpath entries are virtual file refs
    val cp = (Compile / fullClasspath).value
      .map(a => conv.toPath(a.data).toFile)
      .mkString(java.io.File.pathSeparator)
    val out = target.value / outName
    val ni  = sys.env.get("GRAALVM_HOME").map(_ + "/bin/native-image").getOrElse("native-image")
    log.info(s"native-image → $out")
    val rc = scala.sys.process
      .Process(Seq(ni, "--no-fallback", "-cp", cp, mainCls, out.getAbsolutePath))
      .!
    if (rc != 0) sys.error(s"native-image failed (exit $rc)")
    log.info(s"native image built → $out")
  }
)

lazy val commonDeps = Seq(
  libraryDependencies ++= Seq(
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % V.jsoniter,
    "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % V.jsoniter % Provided,
    "org.scalameta"                         %% "munit"                 % V.munit    % Test
  ),
  testFrameworks += new TestFramework("munit.Framework")
)

// ── core: domain model, DB repos (Doobie), discovery clients, markdown ser, sync ────────────────
// TODO: JSON wiring goes through Kindlings' jsoniter integration once its coordinates are pinned;
//       using jsoniter-scala directly for now (reflection-free → native-image friendly either way).
lazy val core = project
  .in(file("modules/core"))
  .settings(name := "histdb-core")
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect"     % V.catsEffect,
      "org.tpolecat"  %% "doobie-core"     % V.doobie,
      "org.tpolecat"  %% "doobie-postgres" % V.doobie,
      "net.postgis"    % "postgis-jdbc"    % V.postgis  // geometry read/written as WKT text via Doobie Meta
    )
  )

// ── server: MCP (JSON-RPC) + REST (Tapir/http4s) + static web UI. Built to a native image. ───────
lazy val server = project
  .in(file("modules/server"))
  .dependsOn(core)
  .settings(name := "histdb-server")
  .settings(commonDeps)
  .settings(nativeImageSettings("histdb.server.Main", "histdb-server"))
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s"                    %% "http4s-ember-server"     % V.http4s,
      "org.http4s"                    %% "http4s-dsl"              % V.http4s,
      "com.softwaremill.sttp.tapir"   %% "tapir-core"              % V.tapir,
      "com.softwaremill.sttp.tapir"   %% "tapir-http4s-server"     % V.tapir,
      "com.softwaremill.sttp.tapir"   %% "tapir-jsoniter-scala"    % V.tapir,
      "ch.qos.logback"                 % "logback-classic"         % V.logback
    )
  )

// ── cli: host-run. Thin server client + local Calibre/file ops. Built to a native image. ─────────
lazy val cli = project
  .in(file("modules/cli"))
  .dependsOn(core)
  .settings(name := "histdb-cli")
  .settings(commonDeps)
  .settings(nativeImageSettings("histdb.cli.Main", "histdb-cli"))
  .settings(
    libraryDependencies ++= Seq(
      "com.monovore" %% "decline-effect"      % V.decline,
      "org.http4s"   %% "http4s-ember-client" % V.http4s
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, server, cli)
  .settings(name := "hist-db", publish / skip := true)
