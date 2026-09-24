// hist-db — containerized Scala MCP server + host CLI over a Postgres/PostGIS store.
// Direct-style SoftwareMill stack: Tapir + tapir-netty-server-sync, Chimp (MCP), Circe (+Kindlings),
// Ox, Magnum (sync JDBC). Toolchain pinned in .sdkmanrc (GraalVM CE 25.0.1 + native-image, sbt 2.0.7)
// and project/build.properties.

import Dependencies.*
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

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

/** VirtusLab Sloth (https://github.com/VirtusLab/sloth) rewrites the lazy-val bytecode of jars that
  * were compiled by Scala < 3.8 (circe, cats, tapir, sttp, decline, sconfig, magnolia…) from the
  * `sun.misc.Unsafe` scheme to the `VarHandle` scheme Scala 3.8 emits. Without it every native binary
  * prints JDK 25's "terminally deprecated method in sun.misc.Unsafe" warning on first lazy val, and
  * JDK 26 removes the method. The Sloth CLI jar is not published; build it with `sbt cli/assembly`
  * in a Sloth checkout and point `SLOTH_JAR` at it (default: a sibling checkout). Absent → the
  * native image is built from the unpatched jars and a log line says so. */
val slothJar = settingKey[Option[File]]("Sloth CLI assembly used to patch pre-3.8 lazy vals before native-image")
ThisBuild / slothJar := {
  val candidates = sys.env.get("SLOTH_JAR").map(file) ++
    (file(sys.props("user.home")) / "Github" / "sloth" / "cli" / "target" * "scala-*" / "sloth.jar").get()
  candidates.find(_.isFile)
}

/** Copy every classpath jar to `target/sloth/` and run Sloth over it (in place, with the whole
  * classpath as type hierarchy so stack-map frames resolve). A copy is re-patched only when the
  * original is newer. Returns the classpath to hand to native-image. */
def slothPatch(log: sbt.util.Logger, sloth: Option[File], entries: Seq[File], outDir: File): Seq[File] =
  sloth match {
    case None =>
      log.warn("sloth: no Sloth jar found (SLOTH_JAR) — native image keeps pre-3.8 lazy vals (sun.misc.Unsafe warning)")
      entries
    case Some(jar) =>
      IO.createDirectory(outDir)
      val jars = entries.filter(_.getName.endsWith(".jar"))
      val dirs = entries.filterNot(_.getName.endsWith(".jar"))
      val copies = jars.map(j => j -> outDir / j.getName)
      val stale  = copies.filter { case (src, dst) => !dst.isFile || dst.lastModified < src.lastModified }
      stale.foreach { case (src, dst) => IO.copyFile(src, dst) }
      val hierarchy = (copies.map(_._2) ++ dirs).mkString(java.io.File.pathSeparator)
      val javaBin = sys.env.get("JAVA_HOME").map(_ + "/bin/java").getOrElse("java")
      stale.foreach { case (_, dst) =>
        val rc = scala.sys.process.Process(Seq(javaBin, "-jar", jar.getAbsolutePath, dst.getAbsolutePath, "--hierarchy-classpath", hierarchy)).!(
          scala.sys.process.ProcessLogger(_ => (), e => if (e.contains("Error")) log.error(s"sloth: $e")))
        if (rc != 0) sys.error(s"sloth failed on ${dst.getName} (exit $rc)")
      }
      log.info(s"sloth: ${stale.size} jar(s) patched, ${copies.size - stale.size} up to date → $outDir")
      copies.map(_._2) ++ dirs
  }

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
//
// On JDK 25 the agent writes ONE `reachability-metadata.json` per module; the legacy split files
// (reflect-config.json, resource-config.json, …) are gone and must not be reintroduced beside it —
// this agent EMPTIES a legacy resource-config.json it finds in the merge dir. Resources are recorded
// as they are requested, so exercise every web asset and boot once against an empty database.
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
      val entries = (Compile / fullClasspath).value.map(a => conv.toPath(a.data).toFile)
      val cp  = slothPatch(log, (ThisBuild / slothJar).value, entries, target.value / "sloth").mkString(java.io.File.pathSeparator)
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

// ── core: the CONTRACT — domain model, wire DTOs, Filter, Tapir endpoint defs, codecs, Markdown.
//    `shared` compiles for JVM and JS (the Scala.js frontend derives its API client from the same
//    endpoints); `jvm` adds config model (sconfig), frontmatter (snakeyaml), export and ArticleNote.
//    NO database code: the CLI depends on coreJVM and nothing else.
lazy val core = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
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
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      // HOCON config modelled as case classes via Kindlings sconfig derivation.
      "com.kubuszok"  %% "kindlings-sconfig-derivation" % V.kindlings,
      "org.ekrich"    %% "sconfig"                      % V.sconfig,
      "org.yaml"       % "snakeyaml"                    % V.snakeyaml
    )
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %% "scala-java-time" % V.scalaJavaTime
    )
  )

lazy val coreJVM = core.jvm
lazy val coreJS  = core.js

// ── db: the store — Magnum repos over Postgres/PostGIS, Flyway migrations, the seed importer. ────
lazy val db = project
  .in(file("modules/db"))
  .dependsOn(coreJVM)
  .settings(name := "histdb-db",
    // Serialized on purpose, for the same reason the server suites are. The suites share ONE
    // Postgres and several rewrite source_chunks/finding_places under each other; and since the
    // DataSource is pooled (`db.pool.max`), a parallel run could park every pooled connection in a
    // transaction blocked on a table lock `PlaceRepo.rebuildFindingPlaces` holds, while the
    // transaction holding it is still queueing FOR a connection.
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
  .dependsOn(coreJVM, db)
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
    // Reachability metadata comes from the tracing agent (META-INF/native-image/reachability-metadata.json)
    // — it covers flexmark's enum reflection, snakeyaml, pgjdbc, flyway, and the config/web resources.
    // The only flag needed is to keep netty's excluded JNI transports at run-time init (→ NIO).
    Seq("--initialize-at-run-time=io.netty.channel.kqueue,io.netty.channel.epoll",
        "--enable-url-protocols=https")
  ))
  .settings(
    Compile / resourceGenerators += Def.task {
      val log = streams.value.log
      val _ = (web / Compile / fastLinkJS).value
      val jsDir = (web / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
      val src = jsDir / "main.js"
      val dst = (Compile / resourceManaged).value / "web" / "app.bundle.js"
      dst.getParentFile.mkdirs()
      val js = IO.read(src).replace("//# sourceMappingURL=main.js.map", "//# sourceMappingURL=app.bundle.js.map")
      IO.write(dst, js)
      val srcMap = jsDir / "main.js.map"
      val dstMap = (Compile / resourceManaged).value / "web" / "app.bundle.js.map"
      if (srcMap.exists()) IO.copyFile(srcMap, dstMap)
      log.info(s"Scala.js frontend → $dst")
      Seq(dst) ++ (if (srcMap.exists()) Seq(dstMap) else Nil)
    },
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
  .dependsOn(coreJVM)
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

// ── web: Scala.js frontend — Laminar + sttp FetchBackend, derived from the same core endpoints. ──
// Dev: `sbt ~web/fastLinkJS` + Vite (`cd modules/web && npx vite`) with proxy to the server.
// Production: the server's resourceGenerator triggers web/fastLinkJS (or fullLinkJS for native-image)
// and copies main.js into the server's classpath as web/app.bundle.js, served alongside vendor/ + CSS.
lazy val web = project
  .in(file("modules/web"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJS)
  .settings(name := "histdb-web")
  .settings(commonDeps)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    libraryDependencies ++= Seq(
      "com.raquo"                     %% "laminar"            % V.laminar,
      "com.softwaremill.sttp.tapir"   %% "tapir-sttp-client4" % V.tapir,
      "com.softwaremill.sttp.client4" %% "core"               % V.sttp
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(coreJVM, coreJS, db, server, cli, web)
  .settings(name := "hist-db", publish / skip := true)
