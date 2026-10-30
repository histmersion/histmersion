// Histmersion — native Scala MCP server + host CLI over a Postgres/PostGIS store.
// Direct-style SoftwareMill stack: Tapir + tapir-netty-server-sync, Chimp (MCP), Circe (+Kindlings),
// Ox, Magnum (sync JDBC). Toolchain (.sdkmanrc, `sdk env install && sdk env`): GraalVM CE 25.4 + native-image,
// pinned in scripts/bundle/graalvm-pins.txt; sbt 2.0.11, also in project/build.properties.

import Dependencies.*

val scala3 = "3.10.0"
ThisBuild / scalaVersion := scala3
ThisBuild / organization := "com.histmersion"
ThisBuild / version := "0.1.0-SNAPSHOT"

// Every module is AGPL-3.0-or-later, plugin-api and the first-party plugins included (LICENSE, NOTICE).
ThisBuild / licenses := List(License("AGPL-3.0-or-later", uri("https://www.gnu.org/licenses/agpl-3.0.html")))
ThisBuild / homepage := Some(uri("https://github.com/histmersion/histmersion"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    uri("https://github.com/histmersion/histmersion"),
    "scm:git:https://github.com/histmersion/histmersion.git",
    Some("scm:git:git@github.com:histmersion/histmersion.git")
  )
)
// name and GitHub handle only: the POM's email field is left empty on purpose
ThisBuild / developers := List(
  Developer("MateuszKubuszok", "Mateusz Kubuszok", "", uri("https://github.com/MateuszKubuszok"))
)
ThisBuild / startYear := Some(2026)
// tapir 1.14 and sttp-ai 0.12 bring sttp-apispec 0.12; Chimp 0.6.0 (the newest) was built against 0.11.10.
// Chimp only builds its tools' input schemas with it, and every MCP suite (tools/list, tools/call over HTTP)
// passes on 0.12 — so 0.12 may evict 0.11 here. Drop this when a Chimp built on 0.12 is out.
ThisBuild / libraryDependencySchemes ++= Seq("jsonschema-circe", "apispec-model")
  .map(m => "com.softwaremill.sttp.apispec" %% m % VersionScheme.Always)

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

// ── sbt 2's disk cache: one per checkout, never the machine-wide one (docs/research-build-speed.md §2) ──
// sbt 2 keeps every cached task result in an action cache (ac/) whose entries point into a
// content-addressed store (cas/), and RESTORES outputs as symlinks into that store. The default store is
// ~/Library/Caches/sbt/v2, shared by every checkout and every sbt process on the machine — so each
// worktree's target/ linked into blobs that other processes wrote, pruned or were writing at that
// moment (no write is atomic), and an action entry whose blob was gone still counted as a hit: the
// checkout got the entry's value without its classes, and the compile failure that followed downstream
// was itself cached and replayed. Here each checkout has its own store, in target/sbt-cache; `rm -rf
// target` (or `sbt cleanFull`) empties it for this checkout alone. The meta-build gets the same line
// in project/plugins.sbt.
Global / localCacheDirectory := appConfiguration.value.baseDirectory.getCanonicalFile / "target" / "sbt-cache"

// ── tests in parallel, within memory ─────────────────────────────────────────────────────────────
// The DB-backed modules (db, server) fork their suites into groups, each group in a JVM of its own on
// a DATABASE of its own (`dbTestGroups` below), so groups never see each other's fixtures; inside a
// group the suites run one at a time, as they always have (several rewrite whole tables). The pure
// modules run in sbt's JVM. At most `HISTMERSION_TEST_FORKS` test JVMs (default 6) run at once.
// (It was one test task at a time — `Tags.exclusive(Tags.Test)`, #1567 — because db and server shared
// ONE database: a server suite rewrote what a db suite was asserting on.)
val testForks = TestGroups.forks(sys.env.get("HISTMERSION_TEST_FORKS"), 6)
// each DB suite's seconds in a measured run, so the groups end together (scripts/dev/test-weights.py)
val testWeights: Map[String, Double] = {
  val f = new File("project/test-weights.tsv")
  if f.isFile then TestGroups.weights(IO.readLines(f)) else Map.empty
}
Global / concurrentRestrictions := Seq(
  Tags.limitAll(java.lang.Runtime.getRuntime.availableProcessors),
  Tags.limit(Tags.ForkedTestGroup, testForks),
  Tags.exclusiveGroup(Tags.Clean)
)

/** A suite's result is cached by sbt 2 (`test`, not `testOnly *`) under its classes' digest. Whether the DB suites ran
  * is part of the result — without the database they skip and "pass" — so the cache key carries it: a pass without the
  * database is never replayed as a pass with it.
  */
val testDbOn = settingKey[Boolean]("HISTMERSION_TEST_DB_URL is set: the DB-backed suites run")
ThisBuild / testDbOn := sys.env.get("HISTMERSION_TEST_DB_URL").exists(_.nonEmpty)

/** One artifact into a Maven repository directory, as `publish` would lay it out: the POM and each `(classifier, file)`
  * (`""` = the main jar) under `<group path>/<name>_<scala>/<version>/`, each with its .sha1/.md5/.sha256. A Scala
  * module's name carries the Scala binary version, as everywhere.
  */
def writeMavenArtifact(
    repo: File,
    module: ModuleID,
    scalaBinary: String,
    pom: File,
    files: Seq[(String, File)]
): Unit = {
  val artifact = s"${module.name}_$scalaBinary"
  val dir = repo / PluginRepo.versionDir(module.organization, artifact, module.revision)
  val base = s"$artifact-${module.revision}"
  def put(name: String, bytes: Array[Byte]): Unit = {
    IO.write(dir / name, bytes)
    PluginRepo.checksums(bytes).foreach { case (ext, sum) => IO.write(dir / s"$name.$ext", sum) }
  }
  put(s"$base.pom", IO.readBytes(pom))
  files.foreach { case (classifier, f) =>
    put(if classifier.isEmpty then s"$base.jar" else s"$base-$classifier.jar", IO.readBytes(f))
  }
}

/** The modules the server resolves at run time, `group:artifact` → version (evicted ones left out). */
def resolvedModules(report: UpdateReport): Map[String, String] =
  report
    .configuration(ConfigRef("runtime"))
    .toSeq
    .flatMap(_.modules)
    .filterNot(_.evicted)
    .map(m => s"${m.module.organization}:${m.module.name}" -> m.module.revision)
    .toMap

val relinkKit =
  taskKey[File]("Write the relink kit: the Sloth-patched class path, its order, the native-image arguments")

// The plugin platform (modules/plugin-api/plugin-platform.txt): the libraries the server provides to plugins.
val pluginPlatformContract = file("modules") / "plugin-api" / "plugin-platform.txt"
val pluginPlatform =
  taskKey[Seq[PluginRepo.Library]]("The plugin platform's libraries at the versions the server resolves")
val pluginRepo = taskKey[File](
  "Write the Maven repository an install ships for plugin authors: plugin-api, plugin-platform and plugin-testkit"
)

lazy val commonDeps = Seq(
  libraryDependencies += "org.scalameta" %% "munit" % V.munit % Test,
  // scala-java-time-tzdb is a time-zone database for Scala.js. scribe's perfolation (1.3.0) depends on its
  // JVM artifact too, where it is dead weight (java.base's own java.time always wins) — and it holds a
  // `java.time.zone.TzdbZoneRulesProvider`, a JDK class name GraalVM 25.4's builder refuses on the image
  // class path. Excluded by its JVM name (`_3`) from every JVM class path — server, CLI, the relink kit,
  // the plugins; Scala.js's `_sjs1_3` is a different module and stays. The binaries' zones are the JDK's
  // own tzdb, checked by `histmersion-server --self-check` (TimeZoneCheck, TimeZoneCheckSuite).
  excludeDependencies += ExclusionRule("io.github.cquiroz", "scala-java-time-tzdb_3"),
  testFrameworks += new TestFramework("munit.Framework"),
  // every compile formats the module's sources first (.scalafmt.conf); `just fmt-check` is the gate
  scalafmtOnCompile := true,
  Test / fork := false,
  Test / extraTestDigests += sbt.util.Digest.sha256Hash(s"test-db=${(ThisBuild / testDbOn).value}".getBytes("UTF-8"))
)

/** The suites of a DB-backed module in `groups` forked JVMs, each on its own database: the `HISTMERSION_TEST_DB_URL`
  * database with the group's name spliced in (`histmersion_server_2_test`), which TestDb creates on first use from a
  * connection to the base one. Suites are dealt longest first by `weights` (seconds, from a measured run; unknown = 1)
  * so the groups end together. In a group the suites run one at a time (`testForkedParallel := false`); the groups run
  * side by side (`parallelExecution := true` folds them in parallel, `testForks` caps them). The fork runs in the build
  * root, as the in-process suites did (they read docs/, skills/, db/ by relative path), and gets
  * `HISTMERSION_SHUTDOWN_GRACE=0`: a test server stops at once instead of waiting 10 s for the test client's idle
  * keep-alive connections.
  */
def dbTestGroups(
    module: String,
    groups: Int,
    heap: String,
    weights: Map[String, Double] = testWeights
): Seq[Setting[?]] = Seq(
  Test / fork := true,
  Test / parallelExecution := true,
  Test / testForkedParallel := false,
  Test / javaOptions ++= Seq(s"-Xmx$heap", "-XX:+UseParallelGC", "-Xss4m"),
  Test / testGrouping := Def.uncached {
    val tests = (Test / definedTests).value
    val opts = (Test / forkOptions).value
    val root = (ThisBuild / baseDirectory).value
    val base = sys.env.get("HISTMERSION_TEST_DB_URL").filter(_.nonEmpty)
    val n = TestGroups.forks(sys.env.get(s"HISTMERSION_TEST_GROUPS_${module.toUpperCase}"), groups)
    TestGroups.split(tests, n)(_.name, s => weights.getOrElse(s.split('.').last, 1.0)).zipWithIndex.map {
      case (ts, i) =>
        val db = base.toSeq.flatMap(b =>
          Seq("HISTMERSION_TEST_DB_URL" -> TestGroups.groupDbUrl(b, s"$module-$i"), "HISTMERSION_TEST_DB_BASE_URL" -> b)
        )
        val env = opts.envVars ++ db ++ Seq("HISTMERSION_SHUTDOWN_GRACE" -> "0")
        Tests.Group(s"$module-$i", ts, Tests.SubProcess(opts.withWorkingDirectory(root).withEnvVars(env)))
    }
  }
)

// ── values: the newtypes every layer speaks of (citekeys, years, periods, DOIs, Wikidata ids,
//    coordinates) and the vocabularies domain, wire and store share (source statuses, edge kinds, place
//    kinds, …), with no codec — Iron only, JVM and JS. Opaque, so the newtypes erase to their base types.
lazy val values = projectMatrix
  .in(file("modules/values"))
  .settings(name := "histmersion-values")
  .settings(commonDeps)
  .settings(libraryDependencies += "io.github.iltotore" %% "iron" % V.iron)
  .jvmPlatform(scalaVersions = Seq(scala3))
  .jsPlatform(scalaVersions = Seq(scala3))

// ── api: what crosses the wire — the wire records, the Tapir endpoints with their circe codecs and
//    schemas (Kindlings), the Markdown every surface renders, and the helpers they need. JVM and JS: the
//    Scala.js frontend derives its client from the same endpoints. Depends on values only.
lazy val api = projectMatrix
  .in(file("modules/api"))
  .dependsOn(values)
  .settings(name := "histmersion-api")
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % V.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % V.tapir,
      "io.circe" %% "circe-core" % V.circe,
      // Kindlings derives circe Codec AND tapir Schema from the same model → they match by construction.
      "com.kubuszok" %% "kindlings-circe-derivation" % V.kindlings,
      "com.kubuszok" %% "kindlings-tapir-schema-derivation" % V.kindlings,
      // the model's newtypes (AGENTS.md, Coding practices 1-2): opaque, so they erase to their base types
      "io.github.iltotore" %% "iron" % V.iron
    )
  )
  .jvmPlatform(scalaVersions = Seq(scala3))
  .jsPlatform(
    scalaVersions = Seq(scala3),
    settings = Seq(libraryDependencies += "io.github.cquiroz" %% "scala-java-time" % V.scalaJavaTime)
  )

// ── config: AppConfig and its readers — HOCON modelled as case classes (Kindlings sconfig derivation) over
//    reference.conf, the user's file and the environment. JVM only. Depends on values only: the server
//    converts what it hands to the wire (the page's `ui` block, the config file's line in /api/about).
lazy val config = project
  .in(file("modules/config"))
  .dependsOn(values.jvm(scala3))
  .settings(name := "histmersion-config")
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.kubuszok" %% "kindlings-sconfig-derivation" % V.kindlings,
      "org.ekrich" %% "sconfig" % V.sconfig
    )
  )

// ── files: the local files a client and the server exchange or keep — the `.hmx` bundle's container and
//    canonical JSON, the merge resolution file, the source and article notes of the Markdown mirror
//    (frontmatter, export), the plugin set and relink kit's records, a directory's size, the gazetteer
//    seed's file names, a book cover's source. JVM only. cli and core both take it, so the CLI's local
//    commands need no domain. Depends on api only.
lazy val files = project
  .in(file("modules/files"))
  .dependsOn(api.jvm(scala3) % "compile->compile;test->test")
  .settings(name := "histmersion-files")
  .settings(commonDeps)
  .settings(libraryDependencies += "org.yaml" % "snakeyaml" % V.snakeyaml)

// ── core: package `domain` — the logic server and db run on. JVM only. It depends on files and api
//    (the wire records the domain works on where both sides have one shape, the pure helpers clients
//    share, and the instances its own derived codecs need). Never on config: the domain takes settings
//    records of its own, which the server converts from config. Its tests read reference.conf. NO
//    database code.
lazy val core = projectMatrix
  .in(file("modules/core"))
  .dependsOn(values, api % "compile->compile;test->test")
  .dependsOn(config % "test->test", files)
  .settings(name := "histmersion-core")
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % V.tapir,
      "io.circe" %% "circe-core" % V.circe,
      "com.kubuszok" %% "kindlings-circe-derivation" % V.kindlings,
      "com.kubuszok" %% "kindlings-tapir-schema-derivation" % V.kindlings,
      "io.github.iltotore" %% "iron" % V.iron
    )
  )
  .settings(
    // the build's pure helper is tested with the other suites (BuildTestGroupsSuite)
    Test / unmanagedSources += (ThisBuild / baseDirectory).value / "project" / "TestGroups.scala",
    // the fixtures convert reference.conf's records into the domain's settings, as the server does
    libraryDependencies += "io.scalaland" %% "chimney" % V.chimney % Test
  )
  .jvmPlatform(scalaVersions = Seq(scala3))

// ── plugin-api: the SPI a plugin compiles against (docs/research-plugins.md §2, decision D1). ──
//    Interfaces, the `Secret` reference type, the lenient config readers and the SPI's own newtypes
//    (years, periods, coordinates, DOIs, Wikidata ids, licences) — nothing else: sconfig +
//    kindlings-sconfig-derivation + iron only (all three in the plugin platform), so no
//    values/api/core/db/server class is ever on a plugin's classpath.
lazy val pluginApi = project
  .in(file("modules/plugin-api"))
  .settings(name := "histmersion-plugin-api")
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "org.ekrich" %% "sconfig" % V.sconfig,
      "com.kubuszok" %% "kindlings-sconfig-derivation" % V.kindlings,
      "io.github.iltotore" %% "iron" % V.iron
    )
  )

// ── plugin-host: the host side of the plugin SPI that does not need the server — discovery, the config
//    contract, secret resolution and staged registration (PluginHost), the acquisition client and its
//    transports, the acquisition chain, and how the host reads a plugin's media collection, event
//    catalogue and AI provider. The server runs it; the plugin testkit runs the SAME code, so a plugin's
//    test sees what the server would. No database, no listener: core, config and plugin-api only.
lazy val pluginHost = project
  .in(file("modules/plugin-host"))
  .dependsOn(core.jvm(scala3) % "compile->compile;test->test", config, pluginApi)
  .settings(name := "histmersion-plugin-host")
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client4" %% "core" % V.sttp,
      "com.outr" %% "scribe" % V.scribe
    )
  )

// ── plugin-testkit: what a plugin's own tests depend on (`//> using test.dep`), shipped in the install's
//    Maven repository beside plugin-api: load the plugin as the server does and run its contributions as
//    the server does (plugin-host's code), with the server's HTTP client live, recording or replaying.
//    Its public API is part of the plugin compatibility contract (plugin-testkit-api.txt).
lazy val pluginTestkit = project
  .in(file("modules/plugin-testkit"))
  .dependsOn(pluginHost)
  .settings(name := "histmersion-plugin-testkit")
  .settings(commonDeps)

// ── plugin-vaults: the first-party secret vaults (KeePassXC, 1Password, Bitwarden) as built-in
//    plugins, DISABLED by default (§3.7, D2). Compiled against plugin-api ONLY, as a third-party plugin
//    would be; the server puts them on its classpath, so they are in every native image.
lazy val pluginVaults = project
  .in(file("modules/plugin-vaults"))
  .dependsOn(pluginApi)
  .settings(name := "histmersion-plugin-vaults")
  .settings(commonDeps)

// ── plugin-fixtures/hello: a TEST plugin (spike A's `hello`), compiled against plugin-api only and
//    packaged as a jar of its own, as a third-party plugin is. Never on the server's class path: the
//    plugin tests and scripts/bundle/smoke.sh's relink drop its jar into a plugins directory.
lazy val pluginHello = project
  .in(file("modules/plugin-fixtures/hello"))
  .dependsOn(pluginApi)
  .settings(name := "histmersion-plugin-hello", publish / skip := true)
  .settings(commonDeps)

// ── plugin-fixtures/hello-ai: a TEST AI provider (docs/research-plugins.md §23) — scripted, no network —
//    compiled against plugin-api only and packaged as a jar of its own. Never on the server's class path:
//    the AI-provider suites load its jar, as the plugin host would a user's plugin.
lazy val pluginHelloAi = project
  .in(file("modules/plugin-fixtures/hello-ai"))
  .dependsOn(pluginApi)
  .settings(name := "histmersion-plugin-hello-ai", publish / skip := true)
  .settings(commonDeps)

// ── plugin-fixtures/hello-lib + hello-deps: a TEST plugin with a dependency of its own. Both are written
//    to a test Maven repository (`pluginHelloDeps/fixturesRepo`) with their POMs, as a plugin author
//    publishes a plugin; hello-deps's POM lists hello-lib, and plugin-api as `provided`. Never on the
//    server's class path: the resolution suites and scripts/bundle/smoke.sh install hello-deps from there.
lazy val pluginHelloLib = project
  .in(file("modules/plugin-fixtures/hello-lib"))
  .settings(name := "histmersion-plugin-hello-lib", version := "0.1.0", publish / skip := true)
  .settings(commonDeps)

val fixturesRepo = taskKey[File]("Write the test Maven repository with hello-lib and hello-deps")

lazy val pluginHelloDeps = project
  .in(file("modules/plugin-fixtures/hello-deps"))
  .dependsOn(pluginApi % Provided, pluginHelloLib)
  .settings(name := "histmersion-plugin-hello-deps", version := "0.1.0", publish / skip := true)
  .settings(commonDeps)
  .settings(
    // UNCACHED: its output is files, not the task's result
    fixturesRepo := Def.uncached {
      val conv = fileConverter.value
      val repo = target.value / "fixtures-repo"
      IO.delete(repo)
      writeMavenArtifact(
        repo,
        (pluginHelloLib / projectID).value,
        (pluginHelloLib / scalaBinaryVersion).value,
        conv.toPath((pluginHelloLib / makePom).value).toFile,
        Seq("" -> conv.toPath((pluginHelloLib / Compile / packageBin).value).toFile)
      )
      writeMavenArtifact(
        repo,
        projectID.value,
        scalaBinaryVersion.value,
        conv.toPath(makePom.value).toFile,
        Seq("" -> conv.toPath((Compile / packageBin).value).toFile)
      )
      repo
    }
  )

// ── plugin-resolver: the dependencies of user plugins (`histmersion plugin add`, the relink, JVM mode),
//    resolved with coursier together with the server's own modules (the relink kit's `modules`): what the
//    server ships is not added again, a plugin that needs a NEWER version of it is refused, the rest is
//    fetched into the plugins directory's `lib/`. Run on the JVM from the relink kit's `tools/resolver/`
//    beside `kit/lib`; never on the server's class path (coursier's jar carries native-image settings of
//    its own, which the server's image must not pick up).
lazy val pluginResolver = project
  .in(file("modules/plugin-resolver"))
  .settings(name := "histmersion-plugin-resolver", publish / skip := true)
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      // the Java API of coursier, everything shaded into one jar; its slf4j-api 1.7 is left out, the
      // kit's 2.x serves it
      ("io.get-coursier" % "interface" % V.coursierInterface).exclude("org.slf4j", "slf4j-api"),
      "io.github.iltotore" %% "iron" % V.iron
    ),
    // the fixture repository (hello-lib, hello-deps) the resolution suite installs from, offline
    Test / resourceGenerators += Def.task {
      val repo = (pluginHelloDeps / fixturesRepo).value
      val out = (Test / resourceManaged).value / "plugin-fixtures-repo.txt"
      IO.write(out, s"repo=${repo.getAbsolutePath}\n")
      Seq(out)
    }.taskValue
  )

// ── chimney-iron: a Chimney macro extension, found by ServiceLoader while Chimney's macros expand. It
//    converts an Iron newtype into the same-named newtype of another layer over the same constraint as
//    `To(from.value)`, with no check (AGENTS.md, Coding practices 4). Compile-time only: a consumer
//    depends on it `% Provided`, and Chimney and Iron are Provided here, so neither the extension nor
//    anything it pulls in is on a consumer's `Runtime / fullClasspath`. (`Compile / fullClasspath`
//    includes Provided jars: the native-image tasks must read the Runtime one before a module that
//    ships a binary depends on this.) It needs Scala 3.10+: Chimney 2
//    and Iron 3.4 are compiled by 3.9 (unreadable to 3.8), and a Scala 3.9.0 compiler bug, fixed in 3.10.0,
//    stops this module's compilation with a cyclic reference.
lazy val chimneyIron = project
  .in(file("modules/chimney-iron"))
  .settings(name := "histmersion-chimney-iron")
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "io.scalaland" %% "chimney" % V.chimney % Provided,
      "io.github.iltotore" %% "iron" % V.iron % Provided
    )
  )

// ── chimney-iron-tests: chimney-iron used the way a layer of Histmersion uses it, `% Provided`. ────
lazy val chimneyIronTests = project
  .in(file("modules/chimney-iron-tests"))
  .dependsOn(chimneyIron % Provided)
  .settings(name := "histmersion-chimney-iron-tests", publish / skip := true)
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "io.scalaland" %% "chimney" % V.chimney,
      "io.github.iltotore" %% "iron" % V.iron
    )
  )

// ── db: the store — Magnum repos over Postgres/PostGIS, Flyway migrations, the seed importer. ────
lazy val db = project
  .in(file("modules/db"))
  .dependsOn(config, core.jvm(scala3) % "compile->compile;test->test")
  .settings(name := "histmersion-db")
  .settings(commonDeps)
  // Inside a group the suites run one at a time on purpose: several rewrite source_chunks/finding_places
  // under each other; and since the DataSource is pooled (`db.pool.max`), suites side by side could park
  // every pooled connection in a transaction blocked on a table lock `PlaceRepo.rebuildFindingPlaces`
  // holds, while the transaction holding it is still queueing FOR a connection. Groups have separate
  // databases, so they cannot.
  .settings(dbTestGroups("db", groups = 2, heap = "1g"))
  .settings(
    // db/migration/index.txt = the list of migration files. Flyway reads it through a ResourceProvider
    // instead of scanning the classpath, which is what makes migrations work inside a native image.
    Compile / resourceGenerators += Def.task {
      val dir = (Compile / resourceDirectory).value / "db" / "migration"
      val names = (dir * "V*.sql").get().map(_.getName).sorted
      val out = (Compile / resourceManaged).value / "db" / "migration" / "index.txt"
      IO.write(out, names.mkString("\n"))
      Seq(out)
    }.taskValue,
    libraryDependencies ++= Seq(
      "com.augustnagro" %% "magnum" % V.magnum,
      "org.postgresql" % "postgresql" % V.postgres,
      "com.zaxxer" % "HikariCP" % V.hikari,
      "org.flywaydb" % "flyway-core" % V.flyway,
      "org.flywaydb" % "flyway-database-postgresql" % V.flyway,
      // Flyway, HikariCP and pgjdbc log through slf4j → scribe (native-image-safe logger; server
      // inherits these). slf4j-api resolves to 2.x (HikariCP), which finds its binding through
      // ServiceLoader — so the provider is `scribe-slf4j2`; the 1.7-style `scribe-slf4j`
      // (StaticLoggerBinder) is invisible to it and every library log was dropped with a
      // "No SLF4J providers were found" warning at startup (#824)
      "com.outr" %% "scribe" % V.scribe,
      "com.outr" %% "scribe-slf4j2" % V.scribe,
      // the merge suites convert reference.conf's merge settings into the domain's, as the server does
      "io.scalaland" %% "chimney" % V.chimney % Test
    )
  )

/** `BuildMeta.scala` for the server: the commit it is built from, that commit's date and whether the checkout had
  * uncommitted changes — what `GET /api/about` reports. `None`s outside a git checkout. Rewritten only when it changes,
  * so an unchanged commit does not recompile the server.
  */
def buildMetaSource(base: File): String = {
  def git(args: String*): Option[String] =
    scala.util
      .Try(scala.sys.process.Process("git" +: args, base).!!(scala.sys.process.ProcessLogger(_ => ())).trim)
      .toOption
      .filter(_.nonEmpty)
  def lit(o: Option[String]) = o.fold("None")(v => "Some(\"" + v.replace("\\", "").replace("\"", "") + "\")")
  val commit = git("rev-parse", "--short=12", "HEAD")
  val date = git("log", "-1", "--format=%cI")
  val dirty = commit.isDefined && git("status", "--porcelain", "--untracked-files=no").isDefined
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
  .dependsOn(core.jvm(scala3), db % "compile->compile;test->test", pluginApi, pluginHost, pluginVaults)
  .settings(name := "histmersion-server", Compile / mainClass := Some("com.histmersion.server.Main"))
  .settings(commonDeps)
  // Inside a group the suites run one at a time on purpose, for two reasons: the backup/restore suite
  // creates+restores a scratch database (parallel suites saw "connection refused" mid-restore), and
  // server startup RECONCILES the OCR job queue (§1.8) — `running`/`queued` rows become `failed:
  // interrupted`, which is right for the one-server deployment but lets two suites booting at once
  // cancel each other's jobs. Groups have separate databases, so neither reaches across them.
  .settings(dbTestGroups("server", groups = 5, heap = "2g"))
  .enablePlugins(NativeImage)
  .settings(
    nativeImageName := "histmersion-server",
    // ONE list, for server/nativeImage and the relink kit, so a relinked binary is linked as the bundled one.
    // netty's excluded JNI transports stay run-time initialised (→ NIO); scribe's SLF4J adapter, which netty's
    // build-time-initialised classes keep in a static field, holds only its name and is safe in the image heap.
    // No --enable-url-protocols (deprecated since GraalVM 25.1: the agent's metadata registers the http/https
    // handlers) and no --no-fallback (no effect since 25.1).
    nativeImageOptions := Seq(
      "--initialize-at-run-time=io.netty.channel.kqueue,io.netty.channel.epoll",
      "--initialize-at-build-time=org.slf4j.ScribeLoggerAdapter"
    )
  )
  .settings(
    // the relink kit (docs/research-plugins.md §7.4, §8.7): what `bin/histmersion-relink` links a
    // binary with user plugins from, without sbt — the Sloth-patched class path the native image is
    // built from, in its order, and the native-image arguments of `nativeImageOptions`. scripts/bundle/
    // server.sh copies it into the bundle and adds the manifest (kit.json, SHA256SUMS).
    // UNCACHED for the reason `nativeImage` is: its output is files, not the task's result.
    relinkKit := Def.uncached {
      val log = streams.value.log
      val conv = fileConverter.value
      val entries = (Compile / fullClasspath).value.map(a => conv.toPath(a.data).toFile)
      val cp = Sloth.patch(
        log,
        (ThisBuild / slothJar).value,
        entries,
        target.value / "sloth",
        (ThisBuild / baseDirectory).value / "target" / "sloth-store"
      )
      val dirs = cp.filterNot(_.getName.endsWith(".jar"))
      if dirs.nonEmpty then
        sys.error(s"relink kit: a class directory on the class path (${dirs.mkString(", ")}) — the kit holds jars only")
      val names = cp.map(_.getName)
      val dup = names.diff(names.distinct).distinct
      if dup.nonEmpty then sys.error(s"relink kit: two class path jars share a file name: ${dup.mkString(", ")}")
      val out = target.value / "kit"
      IO.delete(out)
      cp.foreach(j => IO.copyFile(j, out / "lib" / j.getName))
      IO.writeLines(out / "classpath", names)
      IO.writeLines(out / "native-image.args", nativeImageOptions.value)
      IO.write(out / "main-class", "com.histmersion.server.Main\n")
      // Sloth and its verifier, for the PLUGIN jars a relink links (these are patched already)
      (ThisBuild / slothJar).value.foreach(j => IO.copyFile(j, out / "tools" / "sloth.jar"))
      IO.copyFile(file("scripts") / "bundle" / "SlothVerify.java", out / "tools" / "SlothVerify.java")
      // what the plugin resolver needs: the modules this class path holds (`group:artifact:version`, what a
      // plugin's dependencies are resolved against), the modules the build excludes everywhere, and the
      // resolver itself — its jar and coursier's, run beside lib/ and never part of the image
      IO.writeLines(
        out / "modules",
        resolvedModules((Runtime / update).value).toSeq.sorted.map { case (k, v) => s"$k:$v" }
      )
      IO.writeLines(out / "excludes", excludeDependencies.value.map(r => s"${r.organization}:${r.name}").sorted)
      val resolverOwn = (pluginResolver / Runtime / fullClasspath).value
        .map(a => conv.toPath(a.data).toFile)
        .filter(f => f.getName.startsWith("histmersion-plugin-resolver") || f.getName.startsWith("interface-"))
      if resolverOwn.size != 2 then
        sys.error(s"relink kit: expected the resolver's jar and coursier's, found ${resolverOwn.mkString(", ")}")
      resolverOwn.foreach(j => IO.copyFile(j, out / "tools" / "resolver" / j.getName))
      log.info(s"relink kit: ${cp.size} jar(s) → $out")
      out
    },
    // UNCACHED: a library list has no cache format, and it is read in milliseconds
    pluginPlatform := Def.uncached {
      val contract = PluginRepo
        .parseContract(IO.readLines(pluginPlatformContract))
        .fold(e => sys.error(s"$pluginPlatformContract: $e"), identity)
      PluginRepo
        .shippedVersions(contract, resolvedModules((Runtime / update).value))
        .fold(e => sys.error(s"$pluginPlatformContract: $e"), identity)
    },
    // the Maven repository of the bundle (`<install>/maven`): plugin-api with its sources and POM, and
    // the platform POM, whose dependencies are the platform's libraries at the server's versions.
    // UNCACHED: its output is files, not the task's result
    pluginRepo := Def.uncached {
      val conv = fileConverter.value
      val repo = target.value / "plugin-repo"
      IO.delete(repo)
      val pluginApiId = (pluginApi / projectID).value
      writeMavenArtifact(
        repo,
        pluginApiId,
        (pluginApi / scalaBinaryVersion).value,
        conv.toPath((pluginApi / makePom).value).toFile,
        Seq(
          "" -> conv.toPath((pluginApi / Compile / packageBin).value).toFile,
          "sources" -> conv.toPath((pluginApi / Compile / packageSrc).value).toFile
        )
      )
      // the testkit a plugin's tests depend on, with what it needs of Histmersion's own modules: the plugin
      // host it runs, values, api, files, core, and config, whose settings (reference.conf) give it the server's User-Agent, timeouts
      // and TLS trust. Their other dependencies are ordinary Maven Central libraries their POMs name.
      def module(id: ModuleID, pom: File, jar: File, sources: File): Unit =
        writeMavenArtifact(repo, id, scalaBinaryVersion.value, pom, Seq("" -> jar, "sources" -> sources))
      module(
        (pluginTestkit / projectID).value,
        conv.toPath((pluginTestkit / makePom).value).toFile,
        conv.toPath((pluginTestkit / Compile / packageBin).value).toFile,
        conv.toPath((pluginTestkit / Compile / packageSrc).value).toFile
      )
      module(
        (pluginHost / projectID).value,
        conv.toPath((pluginHost / makePom).value).toFile,
        conv.toPath((pluginHost / Compile / packageBin).value).toFile,
        conv.toPath((pluginHost / Compile / packageSrc).value).toFile
      )
      module(
        (core.jvm(scala3) / projectID).value,
        conv.toPath((core.jvm(scala3) / makePom).value).toFile,
        conv.toPath((core.jvm(scala3) / Compile / packageBin).value).toFile,
        conv.toPath((core.jvm(scala3) / Compile / packageSrc).value).toFile
      )
      module(
        (files / projectID).value,
        conv.toPath((files / makePom).value).toFile,
        conv.toPath((files / Compile / packageBin).value).toFile,
        conv.toPath((files / Compile / packageSrc).value).toFile
      )
      module(
        (config / projectID).value,
        conv.toPath((config / makePom).value).toFile,
        conv.toPath((config / Compile / packageBin).value).toFile,
        conv.toPath((config / Compile / packageSrc).value).toFile
      )
      module(
        (api.jvm(scala3) / projectID).value,
        conv.toPath((api.jvm(scala3) / makePom).value).toFile,
        conv.toPath((api.jvm(scala3) / Compile / packageBin).value).toFile,
        conv.toPath((api.jvm(scala3) / Compile / packageSrc).value).toFile
      )
      module(
        (values.jvm(scala3) / projectID).value,
        conv.toPath((values.jvm(scala3) / makePom).value).toFile,
        conv.toPath((values.jvm(scala3) / Compile / packageBin).value).toFile,
        conv.toPath((values.jvm(scala3) / Compile / packageSrc).value).toFile
      )
      val platformName = s"histmersion-plugin-platform_${scalaBinaryVersion.value}"
      val pom = PluginRepo.platformPom(
        pluginApiId.organization,
        platformName,
        pluginApiId.revision,
        "The libraries Histmersion provides to plugins, at the versions it ships: depend on it compile-only",
        licenses.value.headOption.map(l => l.spdxId -> l.uri.toString).getOrElse("AGPL-3.0-or-later" -> ""),
        pluginPlatform.value
      )
      val pomFile = target.value / "plugin-platform.pom"
      IO.write(pomFile, pom)
      writeMavenArtifact(
        repo,
        pluginApiId.withName("histmersion-plugin-platform"),
        scalaBinaryVersion.value,
        pomFile,
        Nil
      )
      streams.value.log.info(s"plugin repository → $repo")
      repo
    },
    // the test plugins' jars (modules/plugin-fixtures), for the suites that install, describe and check
    // a third-party plugin — never on the server's own class path — and the plugin platform as the
    // contract file has it and as the build resolves it (PluginPlatformSuite compares them)
    Test / resourceGenerators += Def.task {
      val jar = fileConverter.value.toPath((pluginHello / Compile / packageBin).value).toFile
      val ai = fileConverter.value.toPath((pluginHelloAi / Compile / packageBin).value).toFile
      val repo = (pluginHelloDeps / fixturesRepo).value
      val out = (Test / resourceManaged).value / "plugin-fixtures.txt"
      IO.write(out, s"hello=${jar.getAbsolutePath}\nhello-ai=${ai.getAbsolutePath}\nrepo=${repo.getAbsolutePath}\n")
      val contract = (Test / resourceManaged).value / "plugin-platform-contract.txt"
      IO.copyFile(pluginPlatformContract, contract)
      val built = (Test / resourceManaged).value / "plugin-platform-build.txt"
      val resolved = resolvedModules((Runtime / update).value)
      val curated = PluginRepo.parseContract(IO.readLines(pluginPlatformContract)).getOrElse(Nil)
      IO.writeLines(built, curated.map(l => s"${l.key}:${resolved.getOrElse(l.key, "NOT-SHIPPED")}"))
      Seq(out, contract, built)
    }.taskValue
  )
  .settings(
    // UNCACHED: the commit changes without any sbt input changing
    Compile / sourceGenerators += Def.task {
      Def.uncached {
        val out = (Compile / sourceManaged).value / "com" / "histmersion" / "server" / "BuildMeta.scala"
        val src = buildMetaSource(baseDirectory.value)
        if !out.exists() || IO.read(out) != src then IO.write(out, src)
        Seq(out)
      }
    }.taskValue,
    // The agent definitions (docs/research-agent-runtimes.md §2.1) are resources: `agents/<name>.md`
    // (manifest + prompt), the shared fragments `agents/_shared/*.md` (with docs/writing-guide.md as
    // `_shared/writing-guide.md`), and the canonical skills `skills/<name>/SKILL.md` as
    // `agents/skills/<name>/SKILL.md` — ONE copy, rendered per runtime by the server's generator. An
    // `index.txt` lists them, because classpath scanning does not work under native-image.
    // UNCACHED: the inputs are files, not sbt settings.
    Compile / resourceGenerators += Def.task {
      Def.uncached {
        val root = (ThisBuild / baseDirectory).value
        val out = (Compile / resourceManaged).value / "agents"
        val defs = ((root / "agents") * "*.md").get().map(f => f -> f.getName)
        val frags = ((root / "agents" / "_shared") * "*.md").get().map(f => f -> s"_shared/${f.getName}") :+
          ((root / "docs" / "writing-guide.md") -> "_shared/writing-guide.md")
        val skills = ((root / "skills") * "*")
          .get()
          .filter(d => (d / "SKILL.md").isFile)
          .map(d => (d / "SKILL.md") -> s"skills/${d.getName}/SKILL.md")
        val rels = (defs ++ frags ++ skills).sortBy(_._2)
        rels.foreach { case (src, rel) =>
          val dst = out / rel
          if !dst.exists() || IO.read(dst) != IO.read(src) then IO.copyFile(src, dst)
        }
        val index = out / "index.txt"
        val text = rels.map(_._2).mkString("", "\n", "\n")
        if !index.exists() || IO.read(index) != text then IO.write(index, text)
        rels.map(r => out / r._2) :+ index
      }
    }.taskValue,
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
      if srcMap.exists() then IO.copyFile(srcMap, dstMap)
      log.info(s"Scala.js frontend → $dst")
      Seq(dst) ++ (if srcMap.exists() then Seq(dstMap) else Nil)
    },
    libraryDependencies ++= Seq(
      "com.softwaremill.chimp" %% "chimp-server" % V.chimp,
      // the conversions between config, domain and wire records at the server's edges (AGENTS.md, Coding
      // practices 3-4): derived by its macros, so the binary carries only the code they generate
      "io.scalaland" %% "chimney" % V.chimney,
      // exclude netty's JNI native transports → force the pure-Java NIO transport (native-image safe)
      ("com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % V.tapir)
        .exclude("io.netty", "netty-transport-native-kqueue")
        .exclude("io.netty", "netty-transport-native-epoll"),
      "com.softwaremill.sttp.tapir" %% "tapir-files" % V.tapir,
      // sync HTTP CLIENT for the acquisition upstreams (OpenAlex/Crossref/Unpaywall/…): the same
      // java.net.http-backed sttp the CLI uses, so native-image needs no extra machinery.
      "com.softwaremill.sttp.client4" %% "core" % V.sttp,
      "com.vladsch.flexmark" % "flexmark" % V.flexmark,
      // GFM pipe tables: the corpus writes Glossary and comparison tables, which core flexmark shows
      // as rows of pipes. Configured once, in `MarkdownFlavor`, for the pages and the formatting lint.
      "com.vladsch.flexmark" % "flexmark-ext-tables" % V.flexmark,
      // embedded agents (docs/research-embedded-agents.md): provider clients and the tool loop. OFF by
      // default (`agents.enabled`); the agent's tools are typed wrappers over Handlers, in-process.
      "com.softwaremill.sttp.ai" %% "core" % V.sttpAi,
      "com.softwaremill.sttp.ai" %% "claude" % V.sttpAi,
      "com.softwaremill.sttp.ai" %% "openai" % V.sttpAi,
      // the real agent loop against a scripted model (its scalatest matchers are not used)
      "com.softwaremill.sttp.ai" %% "agent-testkit" % V.sttpAi % Test
    )
  )

// ── cli: host-run, PURE HTTP CLIENT of the server (sttp client derived from api's endpoints). ────
lazy val cli = project
  .in(file("modules/cli"))
  .dependsOn(api.jvm(scala3), config, files)
  .settings(name := "histmersion-cli", Compile / mainClass := Some("com.histmersion.cli.Main"))
  .settings(commonDeps)
  .enablePlugins(NativeImage)
  .settings(nativeImageName := "histmersion-cli")
  .settings(
    libraryDependencies ++= Seq(
      "com.monovore" %% "decline" % V.decline,
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-client4" % V.tapir,
      "com.softwaremill.sttp.client4" %% "core" % V.sttp
    )
  )
  .settings(
    // the versions a new plugin project (`histmersion plugin new`) builds with: the Scala this server
    // runs, and the version of the plugin API and platform in the install's Maven repository
    Compile / sourceGenerators += Def.task {
      val out = (Compile / sourceManaged).value / "com" / "histmersion" / "cli" / "PluginBuildVersions.scala"
      val src =
        s"""package com.histmersion.cli
           |
           |/** Generated by build.sbt — do not edit. */
           |object PluginBuildVersions:
           |  val scala: String        = "${scalaVersion.value}"
           |  val organization: String = "${organization.value}"
           |  val histmersion: String  = "${version.value}"
           |  val munit: String        = "${V.munit}"
           |""".stripMargin
      if !out.exists() || IO.read(out) != src then IO.write(out, src)
      Seq(out)
    }.taskValue
  )

// ── web: Scala.js frontend — Laminar + sttp FetchBackend, derived from the same core endpoints. ──
// Dev: `sbt ~web/fastLinkJS` + Vite (`cd modules/web && npx vite`) with proxy to the server.
// Production: the server's resourceGenerator triggers web/fullLinkJS and copies main.js into the
// server's classpath as web/app.bundle.js, served alongside vendor/ + CSS.
lazy val web = project
  .in(file("modules/web"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(api.js(scala3))
  .settings(name := "histmersion-web")
  .settings(commonDeps)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    libraryDependencies ++= Seq(
      "com.raquo" %% "laminar" % V.laminar,
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-client4" % V.tapir,
      "com.softwaremill.sttp.client4" %% "core" % V.sttp
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(
    (values.projectRefs ++ api.projectRefs ++ core.projectRefs)*
  )
  .aggregate(
    config,
    files,
    pluginApi,
    pluginHost,
    pluginTestkit,
    pluginVaults,
    pluginHello,
    pluginHelloAi,
    pluginHelloLib,
    pluginHelloDeps,
    pluginResolver,
    chimneyIron,
    chimneyIronTests,
    db,
    server,
    cli,
    web
  )
  .settings(name := "histmersion", publish / skip := true)
