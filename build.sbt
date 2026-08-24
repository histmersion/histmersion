// hist-db — containerized Scala MCP server + host CLI over a Postgres/PostGIS store.
// Direct-style SoftwareMill stack: Tapir + tapir-netty-server-sync, Chimp (MCP), Circe (+Kindlings),
// Ox, Magnum (sync JDBC). Toolchain pinned in .sdkmanrc (GraalVM CE 25 + native-image, sbt 2.0.7)
// and project/build.properties.

import Dependencies.*

ThisBuild / scalaVersion := "3.8.0"
ThisBuild / organization := "com.kubuszok"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
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
  libraryDependencies += "org.scalameta" %% "munit" % V.munit % Test,
  testFrameworks += new TestFramework("munit.Framework")
)

// ── core: shared CONTRACT — domain model, wire DTOs, Tapir endpoint/tool defs, codecs. No DB. ────
// Both `server` and `cli` depend on this so they agree by construction; keeps the CLI lean.
lazy val core = project
  .in(file("modules/core"))
  .settings(name := "histdb-core")
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"                % V.tapir,
      "io.circe"                    %% "circe-core"                % V.circe,
      "com.kubuszok"                %% "kindlings-circe-derivation" % V.kindlings
    )
  )

// ── server: MCP (Chimp) + REST (Tapir/netty-sync) + web UI; owns the DB. Native image. ───────────
lazy val server = project
  .in(file("modules/server"))
  .dependsOn(core)
  .settings(name := "histdb-server")
  .settings(commonDeps)
  .settings(nativeImageSettings("histdb.server.Main", "histdb-server"))
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.chimp"      %% "chimp-server"            % V.chimp,
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % V.tapir,
      "ch.qos.logback"               % "logback-classic"         % V.logback
    )
  )

// ── cli: host-run. Thin sttp client from `core`'s endpoint defs + local Calibre/file ops. ────────
lazy val cli = project
  .in(file("modules/cli"))
  .dependsOn(core)
  .settings(name := "histdb-cli")
  .settings(commonDeps)
  .settings(nativeImageSettings("histdb.cli.Main", "histdb-cli"))
  .settings(
    libraryDependencies ++= Seq(
      "com.monovore" %% "decline" % V.decline
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, server, cli)
  .settings(name := "hist-db", publish / skip := true)
