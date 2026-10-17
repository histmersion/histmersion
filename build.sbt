// Histmersion — native Scala MCP server + host CLI over a Postgres/PostGIS store.
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
  "-source:future",
  // a pattern match on a non-Matchable type (E165: tapir's `Any` value matchers #829, decoded YAML
  // and JDBC arrays #879) is an ERROR anywhere in the build, so the warning cannot creep back unnoticed
  "-Wconf:id=E165:e"
)

// Dependency versions live in project/Dependencies.scala (imported above as `V`).

// One module's tests at a time. `Test / parallelExecution := false` (db, server) serializes the suites
// INSIDE a module, but sbt still ran the db and server test tasks side by side on the one test
// database, so a server suite could rewrite what a db suite was asserting on — MediaSourcesSuite's
// import job asks about the first pending places/events of the whole database (a db suite's
// fixtures among them), and a finding-places rebuild empties the table a db suite filled by hand
// (#1567). A test task now runs alone (`Tags.limit(Tags.Test, 1)` did not stop the overlap under
// sbt 2: two suites sleeping in db and server still ran at once); the pure suites take seconds.
Global / concurrentRestrictions += Tags.exclusive(Tags.Test)

// ── native-image (custom task; no sbt-2.0 plugin available yet) ─────────────────────────────────
// Shells out to GraalVM `native-image` over the module's full classpath. Reachability metadata
// (captured via the tracing agent) lives in each module's src/main/resources/META-INF/native-image/.
val nativeImage = taskKey[Unit]("Build a GraalVM native image for this module")

/** VirtusLab Sloth (https://github.com/VirtusLab/sloth) rewrites the lazy-val bytecode of jars that
  * were compiled by Scala < 3.8 (circe, cats, tapir, sttp, decline, sconfig, magnolia…) from the
  * `sun.misc.Unsafe` scheme to the `VarHandle` scheme Scala 3.8 emits. Without it every native binary
  * prints JDK 25's "terminally deprecated method in sun.misc.Unsafe" warning on first lazy val, and
  * JDK 26 removes the method. The Sloth CLI jar is not published; build it with `sbt cli/assembly`
  * in a Sloth checkout and point `SLOTH_JAR` at it (default: a checkout next to this repo, as CI
  * makes one, or next to the main worktree when building from a `git worktree`). Absent → the native image is built from the unpatched jars and a log line says so;
  * `scripts/bundle/smoke.sh` then fails on the warning. */
val slothJar = settingKey[Option[File]]("Sloth CLI assembly used to patch pre-3.8 lazy vals before native-image")
ThisBuild / slothJar := {
  // SLOTH_JAR first; then a checkout next to this one, then one next to the MAIN worktree when this
  // is a linked `git worktree` (#874) — see project/SlothLookup.scala
  val repo       = (ThisBuild / baseDirectory).value
  val checkouts  = SlothLookup.checkouts(repo, SlothLookup.gitCommonDir(repo))
  val candidates = sys.env.get("SLOTH_JAR").map(file) ++
    checkouts.flatMap(c => (c / "cli" / "target" * "scala-*" / "sloth.jar").get())
  candidates.find(_.isFile)
}

/** Copy every classpath jar to `target/sloth/` and run Sloth over it (in place, with the whole
  * classpath as type hierarchy so stack-map frames resolve). A copy is re-patched only when the
  * original's CONTENT changed — its SHA-256 is kept beside the copy. (Timestamps were the signal
  * once, and a rebuilt jar of our own went unnoticed: the native binaries shipped stale code.)
  * Returns the classpath to hand to native-image. */
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
      def sha(f: File): String = {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.digest(IO.readBytes(f)).map(b => f"$b%02x").mkString
      }
      def marker(dst: File): File = new File(dst.getPath + ".src-sha256")
      val hashes = copies.map { case (src, _) => src -> sha(src) }.toMap
      val stale  = copies.filter { case (src, dst) =>
        !dst.isFile || !marker(dst).isFile || IO.read(marker(dst)).trim != hashes(src) }
      stale.foreach { case (src, dst) => IO.delete(marker(dst)); IO.copyFile(src, dst) }
      val hierarchy = (copies.map(_._2) ++ dirs).mkString(java.io.File.pathSeparator)
      val javaBin = sys.env.get("JAVA_HOME").map(_ + "/bin/java").getOrElse("java")
      stale.foreach { case (_, dst) =>
        val rc = scala.sys.process.Process(Seq(javaBin, "-jar", jar.getAbsolutePath, dst.getAbsolutePath, "--hierarchy-classpath", hierarchy)).!(
          scala.sys.process.ProcessLogger(_ => (), e => if (e.contains("Error")) log.error(s"sloth: $e")))
        if (rc != 0) sys.error(s"sloth failed on ${dst.getName} (exit $rc)")
      }
      // Sloth has emitted classes the JVM rejects (sttp's SafeGZIPInputStream: every gzip-encoded
      // upstream response failed). The JVM's verifier checks every class it changed; a rejected one
      // gets its original bytes back. See scripts/bundle/SlothVerify.java.
      if (stale.nonEmpty) {
        val verifier = (file("scripts") / "bundle" / "SlothVerify.java").getAbsolutePath
        val pairs    = stale.flatMap { case (src, dst) => Seq(src.getAbsolutePath, dst.getAbsolutePath) }
        val out      = new StringBuilder
        val rc = scala.sys.process.Process(Seq(javaBin, verifier, hierarchy) ++ pairs).!(
          scala.sys.process.ProcessLogger(l => out.append(l).append('\n'), e => out.append(e).append('\n')))
        out.toString.linesIterator.filter(_.nonEmpty).foreach(l => log.warn(s"sloth verify: $l"))
        if (rc != 0) sys.error("sloth verify: a class stays unverifiable after restoring its original bytes")
      }
      // only a copy that was patched AND verified is marked current
      stale.foreach { case (src, dst) => IO.write(marker(dst), hashes(src)) }
      log.info(s"sloth: ${stale.size} jar(s) patched and verified, ${copies.size - stale.size} up to date → $outDir")
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

// The pre-JDK-25 split metadata files. The pinned agent never writes them; finding one beside
// reachability-metadata.json means the app was run under an older JDK's agent — and with both formats
// present the JDK-25 agent empties resource-config.json, silently dropping every resource. Refuse to
// build rather than ship a mixed image (a .gitignore would only hide them; native-image still reads them).
val legacyNativeConfigs = Seq("jni-config.json", "predefined-classes-config.json", "proxy-config.json",
  "reflect-config.json", "resource-config.json", "serialization-config.json")

def checkNoLegacyNativeConfigs(dir: File): Unit = {
  val found = legacyNativeConfigs.map(dir / _).filter(_.exists)
  if (found.nonEmpty)
    sys.error(
      s"legacy native-image metadata beside reachability-metadata.json: ${found.map(_.getName).mkString(", ")} in $dir\n" +
        "  these come from a tracing-agent run under a JDK other than the one pinned in .sdkmanrc.\n" +
        "  Delete them, then regenerate with the pinned JDK (CLAUDE.md, 'The tracing-agent rule').")
}

// NOTE: `Compile / mainClass` is set inline in each project, NOT here — sbt 2 identifies settings by
// source position, so a `:=` inside a shared def would make the last caller win for every project.
def nativeImageSettings(mainCls: String, outName: String, extraOpts: Seq[String] = Nil): Seq[Setting[?]] =
  Seq(
    // UNCACHED: sbt 2 caches task results by default, but the binary this writes is not part of the
    // result — a restored CI cache reported the task done and no binary existed (bundle.yml, 2026-09-25)
    nativeImage := Def.uncached {
      val log  = streams.value.log
      val conv = fileConverter.value // sbt 2.0: classpath entries are virtual file refs
      val entries = (Compile / fullClasspath).value.map(a => conv.toPath(a.data).toFile)
      val cp  = slothPatch(log, (ThisBuild / slothJar).value, entries, target.value / "sloth").mkString(java.io.File.pathSeparator)
      val out = target.value / outName
      // a killed agent run leaves agent-pid*/ scratch dirs in the merge dir; never let them in
      IO.delete(((Compile / resourceDirectory).value / "META-INF" / "native-image" * "agent-pid*").get())
      checkNoLegacyNativeConfigs((Compile / resourceDirectory).value / "META-INF" / "native-image")
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
  .settings(name := "histmersion-core")
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
  .settings(name := "histmersion-db",
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
      // Flyway, HikariCP and pgjdbc log through slf4j → scribe (native-image-safe logger; server
      // inherits these). slf4j-api resolves to 2.x (HikariCP), which finds its binding through
      // ServiceLoader — so the provider is `scribe-slf4j2`; the 1.7-style `scribe-slf4j`
      // (StaticLoggerBinder) is invisible to it and every library log was dropped with a
      // "No SLF4J providers were found" warning at startup (#824)
      "com.outr"        %% "scribe"                     % V.scribe,
      "com.outr"        %% "scribe-slf4j2"              % V.scribe
    )
  )

/** `BuildMeta.scala` for the server: the commit it is built from, that commit's date and whether the
  * checkout had uncommitted changes — what `GET /api/about` reports. `None`s outside a git checkout.
  * Rewritten only when it changes, so an unchanged commit does not recompile the server. */
def buildMetaSource(base: File): String = {
  def git(args: String*): Option[String] =
    scala.util.Try(scala.sys.process.Process("git" +: args, base).!!(scala.sys.process.ProcessLogger(_ => ())).trim)
      .toOption.filter(_.nonEmpty)
  def lit(o: Option[String]) = o.fold("None")(v => "Some(\"" + v.replace("\\", "").replace("\"", "") + "\")")
  val commit = git("rev-parse", "--short=12", "HEAD")
  val date   = git("log", "-1", "--format=%cI")
  val dirty  = commit.isDefined && git("status", "--porcelain", "--untracked-files=no").isDefined
  s"""package com.histmersion.server
     |
     |/** Generated by build.sbt (buildMetaSource) — do not edit. */
     |object BuildMeta:
     |  val gitCommit: Option[String]  = ${lit(commit)}
     |  val commitDate: Option[String] = ${lit(date)}
     |  val dirty: Boolean             = $dirty
     |""".stripMargin
}

// ── server: MCP (Chimp) + REST (Tapir/netty-sync) + web UI; the only DB client. Native image. ───
lazy val server = project
  .in(file("modules/server"))
  .dependsOn(coreJVM, db % "compile->compile;test->test")
  .settings(name := "histmersion-server", Compile / mainClass := Some("com.histmersion.server.Main"),
    // Serialized on purpose, for two reasons: the backup/restore suite creates+restores a scratch
    // database (parallel suites saw "connection refused" mid-restore), and server startup RECONCILES
    // the OCR job queue (§1.8) — `running`/`queued` rows become `failed: interrupted`, which is right
    // for the one-server deployment but lets two suites booting at once cancel each other's jobs.
    Test / parallelExecution := false)
  .settings(commonDeps)
  .settings(writeClasspathSetting).settings(agentRunSettings)
  .settings(nativeImageSettings(
    "com.histmersion.server.Main",
    "histmersion-server",
    // Reachability metadata comes from the tracing agent (META-INF/native-image/reachability-metadata.json)
    // — it covers flexmark's enum reflection, snakeyaml, pgjdbc, flyway, and the config/web resources.
    // Flags: keep netty's excluded JNI transports at run-time init (→ NIO); and let scribe's SLF4J
    // logger adapter be created at build time. Netty classes that are build-time initialised keep an
    // SLF4J logger in a static field, which since #824 is a scribe adapter. It holds only its name and
    // looks the scribe configuration up on every call, so baking it into the image heap is safe.
    Seq("--initialize-at-run-time=io.netty.channel.kqueue,io.netty.channel.epoll",
        "--initialize-at-build-time=org.slf4j.ScribeLoggerAdapter",
        "--enable-url-protocols=https")
  ))
  .settings(
    // UNCACHED: the commit changes without any sbt input changing
    Compile / sourceGenerators += Def.task { Def.uncached {
      val out = (Compile / sourceManaged).value / "com" / "histmersion" / "server" / "BuildMeta.scala"
      val src = buildMetaSource(baseDirectory.value)
      if (!out.exists() || IO.read(out) != src) IO.write(out, src)
      Seq(out)
    } }.taskValue,
    // The agent definitions (docs/research-agent-runtimes.md §2.1) are resources: `agents/<name>.md`
    // (manifest + prompt), the shared fragments `agents/_shared/*.md` (with docs/writing-guide.md as
    // `_shared/writing-guide.md`), and the canonical skills `skills/<name>/SKILL.md` as
    // `agents/skills/<name>/SKILL.md` — ONE copy, rendered per runtime by the server's generator. An
    // `index.txt` lists them, because classpath scanning does not work under native-image.
    // UNCACHED: the inputs are files, not sbt settings.
    Compile / resourceGenerators += Def.task { Def.uncached {
      val root  = (ThisBuild / baseDirectory).value
      val out   = (Compile / resourceManaged).value / "agents"
      val defs  = ((root / "agents") * "*.md").get().map(f => f -> f.getName)
      val frags = ((root / "agents" / "_shared") * "*.md").get().map(f => f -> s"_shared/${f.getName}") :+
        ((root / "docs" / "writing-guide.md") -> "_shared/writing-guide.md")
      val skills = ((root / "skills") * "*").get().filter(d => (d / "SKILL.md").isFile)
        .map(d => (d / "SKILL.md") -> s"skills/${d.getName}/SKILL.md")
      val rels  = (defs ++ frags ++ skills).sortBy(_._2)
      rels.foreach { case (src, rel) =>
        val dst = out / rel
        if (!dst.exists() || IO.read(dst) != IO.read(src)) IO.copyFile(src, dst)
      }
      val index = out / "index.txt"
      val text  = rels.map(_._2).mkString("", "\n", "\n")
      if (!index.exists() || IO.read(index) != text) IO.write(index, text)
      rels.map(r => out / r._2) :+ index
    } }.taskValue,
    Compile / resourceGenerators += Def.task {
      val log = streams.value.log
      // the FULL link is what the server ships (#1949): the optimiser's output with production
      // semantics — no null check, cast check or bounds check on every access (fastLinkJS keeps them:
      // 31 MB, against 18 MB here), 12 % less heap at load and a whole-range boundaries load 900 → 580 ms
      // (docs/research-web-perf.md). `sbt ~web/fastLinkJS` + Vite stays the quick loop for development.
      val _ = (web / Compile / fullLinkJS).value
      val jsDir = (web / Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value
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
      "com.vladsch.flexmark"         % "flexmark"                % V.flexmark,
      // GFM pipe tables: the corpus writes Glossary and comparison tables, which core flexmark shows
      // as rows of pipes. Configured once, in `MarkdownFlavor`, for the pages and the formatting lint.
      "com.vladsch.flexmark"         % "flexmark-ext-tables"     % V.flexmark,
      // embedded agents (docs/research-embedded-agents.md): provider clients and the tool loop. OFF by
      // default (`agents.enabled`); the agent's tools are typed wrappers over Handlers, in-process.
      "com.softwaremill.sttp.ai"    %% "core"                    % V.sttpAi,
      "com.softwaremill.sttp.ai"    %% "claude"                  % V.sttpAi,
      "com.softwaremill.sttp.ai"    %% "openai"                  % V.sttpAi,
      // the real agent loop against a scripted model (its scalatest matchers are not used)
      "com.softwaremill.sttp.ai"    %% "agent-testkit"           % V.sttpAi % Test
    )
  )

// ── cli: host-run, PURE HTTP CLIENT of the server (sttp client derived from core's endpoints). ───
lazy val cli = project
  .in(file("modules/cli"))
  .dependsOn(coreJVM)
  .settings(name := "histmersion-cli", Compile / mainClass := Some("com.histmersion.cli.Main"))
  .settings(commonDeps)
  .settings(writeClasspathSetting)
  .settings(agentRunSettings)
  .settings(nativeImageSettings("com.histmersion.cli.Main", "histmersion-cli"))
  .settings(
    libraryDependencies ++= Seq(
      "com.monovore"                %% "decline"             % V.decline,
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-client4"  % V.tapir,
      "com.softwaremill.sttp.client4" %% "core"              % V.sttp
    )
  )

// ── web: Scala.js frontend — Laminar + sttp FetchBackend, derived from the same core endpoints. ──
// Dev: `sbt ~web/fastLinkJS` + Vite (`cd modules/web && npx vite`) with proxy to the server.
// Production: the server's resourceGenerator triggers web/fullLinkJS and copies main.js into the
// server's classpath as web/app.bundle.js, served alongside vendor/ + CSS.
lazy val web = project
  .in(file("modules/web"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJS)
  .settings(name := "histmersion-web")
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
  .settings(name := "histmersion", publish / skip := true)
