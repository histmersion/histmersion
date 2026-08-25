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

// Writes the module's Compile classpath to target/classpath.txt so the app can be run under the
// GraalVM native-image tracing agent directly (no sbt classes polluting the collected metadata).
val writeClasspath = taskKey[Unit]("Write the module classpath to target/classpath.txt")
def writeClasspathSetting: Seq[Setting[?]] = Seq(
  writeClasspath := {
    val conv = fileConverter.value
    val cp   = (Compile / fullClasspath).value
      .map(a => conv.toPath(a.data).toFile)
      .mkString(java.io.File.pathSeparator)
    val f = target.value / "classpath.txt"
    IO.write(f, cp)
    streams.value.log.info(s"classpath → $f")
  }
)

// Runs the app (forked `run`/`runMain`) under the GraalVM native-image tracing agent, MERGING the
// captured reflection/resource/JNI metadata into src/main/resources/META-INF/native-image. So simply
// exercising the app via sbt keeps the native-image metadata current — no separate command to run.
// (Requires the GraalVM JDK from .sdkmanrc, which ships the agent.)
def agentRunSettings: Seq[Setting[?]] = {
  def agentOpt(resourceDir: File): String =
    val dir = resourceDir / "META-INF" / "native-image"
    IO.createDirectory(dir)
    s"-agentlib:native-image-agent=config-merge-dir=${dir.getAbsolutePath}"
  Seq(
    Compile / run / fork := true,
    Compile / runMain / fork := true,
    Compile / run / javaOptions += agentOpt((Compile / resourceDirectory).value),
    Compile / runMain / javaOptions += agentOpt((Compile / resourceDirectory).value)
  )
}

def nativeImageSettings(mainCls: String, outName: String, extraOpts: Seq[String] = Nil): Seq[Setting[?]] =
  Seq(
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
      val cmd = Seq(ni, "--no-fallback") ++ extraOpts ++ Seq("-cp", cp, mainCls, out.getAbsolutePath)
      val rc  = scala.sys.process.Process(cmd).!
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
      "com.softwaremill.sttp.tapir" %% "tapir-core"                       % V.tapir,
      "io.circe"                    %% "circe-core"                       % V.circe,
      // Kindlings derives circe Codec AND tapir Schema from the same model → they match by construction.
      "com.kubuszok"                %% "kindlings-circe-derivation"        % V.kindlings,
      "com.kubuszok"                %% "kindlings-tapir-schema-derivation" % V.kindlings,
      // HOCON config modelled as case classes via Kindlings sconfig derivation.
      "com.kubuszok"                %% "kindlings-sconfig-derivation"      % V.kindlings,
      "org.ekrich"                  %% "sconfig"                          % V.sconfig,
      // DB (store of record) + frontmatter parsing for the seed importer.
      "com.augustnagro"             %% "magnum"                           % V.magnum,
      "org.postgresql"               % "postgresql"                       % V.postgres,
      "org.yaml"                     % "snakeyaml"                         % V.snakeyaml
    )
  )

// ── server: MCP (Chimp) + REST (Tapir/netty-sync) + web UI; owns the DB. Native image. ───────────
lazy val server = project
  .in(file("modules/server"))
  .dependsOn(core)
  .settings(name := "histdb-server")
  .settings(commonDeps)
  .settings(writeClasspathSetting).settings(agentRunSettings)
  .settings(nativeImageSettings(
    "histdb.server.Main",
    "histdb-server",
    // Reflection/resource/JNI metadata comes from the tracing agent (META-INF/native-image/*.json) —
    // it covers flexmark's enum reflection, snakeyaml, pgjdbc, and the config/web resources. The only
    // flag needed is to keep netty's excluded JNI transports at run-time init (they fall back to NIO).
    // scribe avoids the logger-in-heap issue, and config loads eagerly on the main thread.
    Seq("--initialize-at-run-time=io.netty.channel.kqueue,io.netty.channel.epoll")
  ))
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.chimp"      %% "chimp-server"            % V.chimp,
      // exclude netty's JNI native transports → force the pure-Java NIO transport (native-image safe)
      ("com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % V.tapir)
        .exclude("io.netty", "netty-transport-native-kqueue")
        .exclude("io.netty", "netty-transport-native-epoll"),
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % V.tapir,
      "com.vladsch.flexmark"         % "flexmark"                % V.flexmark,
      "com.outr"                    %% "scribe"                  % V.scribe,
      "com.outr"                    %% "scribe-slf4j"            % V.scribe // routes netty/tapir slf4j logs → scribe
    )
  )

// ── cli: host-run. Thin sttp client from `core`'s endpoint defs + local Calibre/file ops. ────────
lazy val cli = project
  .in(file("modules/cli"))
  .dependsOn(core)
  .settings(name := "histdb-cli")
  .settings(commonDeps)
  .settings(writeClasspathSetting)
  .settings(agentRunSettings)
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
