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

// NOTE: `Compile / mainClass` is set inline in each project, NOT here — sbt 2 identifies settings by
// source position, so a `:=` inside a shared def would make the last caller win for every project.
def nativeImageSettings(mainCls: String, outName: String, extraOpts: Seq[String] = Nil): Seq[Setting[?]] =
  Seq(
    nativeImage := {
      val log  = streams.value.log
      val conv = fileConverter.value // sbt 2.0: classpath entries are virtual file refs
      val cp = (Compile / fullClasspath).value
        .map(a => conv.toPath(a.data).toFile)
        .mkString(java.io.File.pathSeparator)
      val out = target.value / outName
      // a killed agent run leaves agent-pid*/ scratch dirs in the merge dir; never let them in
      IO.delete(((Compile / resourceDirectory).value / "META-INF" / "native-image" * "agent-pid*").get())
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
  testFrameworks += new TestFramework("munit.Framework"),
  Test / fork := false
)

// ── core: the CONTRACT — domain model, wire DTOs, Filter, Tapir endpoint defs, codecs, Markdown,
//    config model, frontmatter parsing. NO database code: the CLI depends on this and nothing else.
lazy val core = project
  .in(file("modules/core"))
  .settings(name := "histdb-core")
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"                       % V.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"                 % V.tapir,
      "io.circe"                    %% "circe-core"                       % V.circe,
      // Kindlings derives circe Codec AND tapir Schema from the same model → they match by construction.
      "com.kubuszok"                %% "kindlings-circe-derivation"        % V.kindlings,
      "com.kubuszok"                %% "kindlings-tapir-schema-derivation" % V.kindlings,
      // HOCON config modelled as case classes via Kindlings sconfig derivation.
      "com.kubuszok"                %% "kindlings-sconfig-derivation"      % V.kindlings,
      "org.ekrich"                  %% "sconfig"                          % V.sconfig,
      "org.yaml"                     % "snakeyaml"                        % V.snakeyaml
    )
  )

// ── db: the store — Magnum repos over Postgres/PostGIS, Flyway migrations, the seed importer. ────
lazy val db = project
  .in(file("modules/db"))
  .dependsOn(core)
  .settings(name := "histdb-db",
    // Serialized on purpose, for the same reason the server suites are: these suites all point at
    // ONE database and one (now pooled, `db.pool.max`) DataSource. `PlaceRepo.rebuildFindingPlaces`
    // takes table locks, so a parallel run could park every pooled connection in a transaction that
    // is blocked on a lock held by a transaction still queueing FOR a connection — a deadlock that
    // is a property of the test layout, not of the pool size.
    Test / parallelExecution := false)
  .settings(commonDeps)
  .settings(
    // db/migration/index.txt = the list of migration files. Flyway reads it through a ResourceProvider
    // instead of scanning the classpath, which is what makes migrations work inside a native image.
    Compile / resourceGenerators += Def.task {
      val dir   = (Compile / resourceDirectory).value / "db" / "migration"
      val names = (dir * "V*.sql").get().map(_.getName).sorted
      val out   = (Compile / resourceManaged).value / "db" / "migration" / "index.txt"
      IO.write(out, names.mkString("\n"))
      Seq(out)
    }.taskValue,
    libraryDependencies ++= Seq(
      "com.augustnagro" %% "magnum"                     % V.magnum,
      "org.postgresql"   % "postgresql"                 % V.postgres,
      "com.zaxxer"       % "HikariCP"                   % V.hikari,
      "org.flywaydb"     % "flyway-core"                % V.flyway,
      "org.flywaydb"     % "flyway-database-postgresql" % V.flyway,
      // Flyway logs through slf4j → scribe (native-image-safe logger; server inherits these)
      "com.outr"        %% "scribe"                     % V.scribe,
      "com.outr"        %% "scribe-slf4j"               % V.scribe
    )
  )

// ── server: MCP (Chimp) + REST (Tapir/netty-sync) + web UI; the only DB client. Native image. ───
lazy val server = project
  .in(file("modules/server"))
  .dependsOn(core, db)
  .settings(name := "histdb-server", Compile / mainClass := Some("histdb.server.Main"),
    // Serialized on purpose, for two reasons: the backup/restore suite creates+restores a scratch
    // database (parallel suites saw "connection refused" mid-restore), and server startup RECONCILES
    // the OCR job queue (§1.8) — `running`/`queued` rows become `failed: interrupted`, which is right
    // for the one-server deployment but lets two suites booting at once cancel each other's jobs.
    Test / parallelExecution := false)
  .settings(commonDeps)
  .settings(writeClasspathSetting).settings(agentRunSettings)
  .settings(nativeImageSettings(
    "histdb.server.Main",
    "histdb-server",
    // Reflection/resource/JNI metadata comes from the tracing agent (META-INF/native-image/*.json) —
    // it covers flexmark's enum reflection, snakeyaml, pgjdbc, flyway, and the config/web resources.
    // The only flag needed is to keep netty's excluded JNI transports at run-time init (→ NIO).
    Seq("--initialize-at-run-time=io.netty.channel.kqueue,io.netty.channel.epoll")
  ))
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.chimp"      %% "chimp-server"            % V.chimp,
      // exclude netty's JNI native transports → force the pure-Java NIO transport (native-image safe)
      ("com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % V.tapir)
        .exclude("io.netty", "netty-transport-native-kqueue")
        .exclude("io.netty", "netty-transport-native-epoll"),
      "com.softwaremill.sttp.tapir" %% "tapir-files"             % V.tapir,
      // sync HTTP CLIENT for the acquisition upstreams (OpenAlex/Crossref/Unpaywall/…): the same
      // java.net.http-backed sttp the CLI uses, so native-image needs no extra machinery.
      "com.softwaremill.sttp.client4" %% "core"                   % V.sttp,
      "com.vladsch.flexmark"         % "flexmark"                % V.flexmark
    )
  )

// ── cli: host-run, PURE HTTP CLIENT of the server (sttp client derived from core's endpoints). ───
lazy val cli = project
  .in(file("modules/cli"))
  .dependsOn(core)
  .settings(name := "histdb-cli", Compile / mainClass := Some("histdb.cli.Main"))
  .settings(commonDeps)
  .settings(writeClasspathSetting)
  .settings(agentRunSettings)
  .settings(nativeImageSettings("histdb.cli.Main", "histdb-cli"))
  .settings(
    libraryDependencies ++= Seq(
      "com.monovore"                %% "decline"             % V.decline,
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-client4"  % V.tapir,
      "com.softwaremill.sttp.client4" %% "core"              % V.sttp
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, db, server, cli)
  .settings(name := "hist-db", publish / skip := true)
