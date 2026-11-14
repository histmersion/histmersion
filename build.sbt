// Histmersion — native Scala MCP server + host CLI over a Postgres/PostGIS store.
// Direct-style SoftwareMill stack: Tapir + tapir-netty-server-sync, Chimp (MCP), jsoniter-scala (+Kindlings),
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
ThisBuild / libraryDependencySchemes ++= Seq("jsonschema-circe", "apispec-model", "openapi-model")
  .map(m => "com.softwaremill.sttp.apispec" %% m % VersionScheme.Always)

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-source:future",
  // a pattern match on a non-Matchable type (E165: tapir's `Any` value matchers #829, decoded YAML
  // and JDBC arrays #879) is an ERROR anywhere in the build, so the warning cannot creep back unnoticed
  "-Wconf:id=E165:e",
  // `==` and `!=` compile only between types with a `CanEqual` (G53): a newtype's own (its `Newtype` base), an enum's or
  // record's `derives CanEqual`, the standard library's; comparing two unrelated types is a compile error
  "-language:strictEquality"
)

// Every compile writes its SemanticDB (beside the classes, in `meta`/`test-meta`, never into a jar): the guideline
// ratchet (GuidelineRatchetSuite, server tests) reads it, so the check needs no separate build.
ThisBuild / semanticdbEnabled := true

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

/** What a POM declares it needs at run time: `group:artifact` of each dependency that is neither optional nor test- or
  * provided-scoped.
  */
def pomNeeds(pom: File): (String, Seq[String]) = {
  val xml = scala.xml.XML.loadFile(pom)
  val self = s"${(xml \ "groupId").headOption.fold("")(_.text.trim)}:${(xml \ "artifactId").text.trim}"
  val needs = (xml \ "dependencies" \ "dependency")
    .filterNot(d => (d \ "optional").text.trim == "true" || Set("test", "provided", "system")((d \ "scope").text.trim))
    .map(d => s"${(d \ "groupId").text.trim}:${(d \ "artifactId").text.trim}")
  self -> needs
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

// The base trait of every newtype's companion (CanEqual and the base type's Ordering, lifted once), on Iron alone
lazy val newtype = projectMatrix
  .in(file("modules/newtype"))
  .settings(
    name := "histmersion-newtype",
    description := "The base trait of every Histmersion newtype: an Iron RefinedType with CanEqual and a lifted Ordering"
  )
  .settings(commonDeps)
  // Hearth prints the companion's type for its name at compile time (CompanionMacro): the version Kindlings uses
  .settings(libraryDependencies ++= Seq("io.github.iltotore" %% "iron" % V.iron, "com.kubuszok" %% "hearth" % V.hearth))
  .jvmPlatform(scalaVersions = Seq(scala3))
  .jsPlatform(scalaVersions = Seq(scala3))

// The one closed failure type of the application (Problem, Problems, the outside text it carries) and its English
// sentence (Text), on Iron alone: the codecs, the statuses and the stored wording are the api's. The sentence lives here
// because the modules below the api render problems too (the plugin resolver's console, the plugins' log lines)
// A vector with at least one element: the one non-empty collection of the application (Problems is a problem or a
// NonEmptyVector of them), on the standard library alone
lazy val nonEmpty = projectMatrix
  .in(file("modules/nonempty"))
  .settings(
    name := "histmersion-nonempty",
    description := "NonEmptyVector: a vector with at least one element, the vector itself at run time"
  )
  .settings(commonDeps)
  .jvmPlatform(scalaVersions = Seq(scala3))
  .jsPlatform(scalaVersions = Seq(scala3))

// The Hearth extension that tells Kindlings' derivations a NonEmptyVector is a collection read from a non-empty list:
// compile time only, so every module that derives a codec or a schema depends on it % Provided (on its macro class path,
// never in a binary)
lazy val nonEmptyDerivation = projectMatrix
  .in(file("modules/nonempty-derivation"))
  .dependsOn(nonEmpty, json % "test->compile")
  .settings(
    name := "histmersion-nonempty-derivation",
    description := "A Hearth IsCollection provider for NonEmptyVector, so that Kindlings derives its codecs and schemas"
  )
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.kubuszok" %% "hearth" % V.hearth,
      compilerPlugin("com.kubuszok" %% "hearth-cross-quotes" % V.hearth),
      // its suite derives as a consumer does: a codec and a schema of a record holding a NonEmptyVector
      "com.softwaremill.sttp.tapir" %% "tapir-core" % V.tapir % Test,
      "com.kubuszok" %% "kindlings-tapir-schema-derivation" % V.kindlings % Test
    )
  )
  .jvmPlatform(scalaVersions = Seq(scala3))
  .jsPlatform(scalaVersions = Seq(scala3))

lazy val problem = projectMatrix
  .in(file("modules/problem"))
  .dependsOn(newtype, nonEmpty)
  .settings(
    name := "histmersion-problem",
    description :=
      "The closed Problem of the application, its accumulation (Problems), the outside text it carries and its sentence"
  )
  .settings(commonDeps)
  .settings(libraryDependencies += "io.github.iltotore" %% "iron" % V.iron)
  .jvmPlatform(scalaVersions = Seq(scala3))
  .jsPlatform(
    scalaVersions = Seq(scala3),
    settings = Seq(libraryDependencies += "io.github.cquiroz" %% "scala-java-time" % V.scalaJavaTime)
  )

// JSON without circe: the derived record codec, the base types' and containers' codecs, raw JSON (Kindlings' AST) and a
// cursor over it; shared by the wire contract and the plugin SPI
lazy val json = projectMatrix
  .in(file("modules/json"))
  .settings(
    name := "histmersion-json",
    description := "JSON for Histmersion and its plugins: derived jsoniter codecs, raw JSON (Kindlings' AST) and a cursor"
  )
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % V.jsoniter,
      "com.kubuszok" %% "kindlings-jsoniter-derivation" % V.kindlings,
      "com.kubuszok" %% "kindlings-jsoniter-json" % V.kindlings
    )
  )
  .jvmPlatform(scalaVersions = Seq(scala3))
  .jsPlatform(scalaVersions = Seq(scala3))

lazy val values = projectMatrix
  .in(file("modules/values"))
  .dependsOn(newtype, problem)
  .settings(
    name := "histmersion-values",
    description := "The model's newtypes and vocabularies the server-side modules share, on Iron alone (JVM)"
  )
  .settings(commonDeps)
  .settings(libraryDependencies += "io.github.iltotore" %% "iron" % V.iron)
  .jvmPlatform(scalaVersions = Seq(scala3))

lazy val api = projectMatrix
  .in(file("modules/api"))
  .dependsOn(nonEmptyDerivation % Provided)
  .dependsOn(newtype, problem, json)
  .settings(
    name := "histmersion-api",
    description := "The wire contract: records, Tapir endpoints, jsoniter codecs and schemas, Markdown (JVM and Scala.js)"
  )
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % V.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-jsoniter-scala" % V.tapir,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % V.jsoniter,
      // Kindlings derives the jsoniter codec AND the tapir Schema from the same model → they match by construction.
      "com.kubuszok" %% "kindlings-jsoniter-derivation" % V.kindlings,
      "com.kubuszok" %% "kindlings-tapir-schema-derivation" % V.kindlings,
      // JSON no record describes (model answers, tool schemas, upstream replies, jsonb documents), without circe
      "com.kubuszok" %% "kindlings-jsoniter-json" % V.kindlings,
      // lenses: one field of a record updated without spelling out the copy
      "com.kubuszok" %% "kindlings-optics" % V.kindlings,
      // the model's newtypes (AGENTS.md, Coding practices 1-2): opaque, so they erase to their base types
      "io.github.iltotore" %% "iron" % V.iron
    )
  )
  .jvmPlatform(scalaVersions = Seq(scala3))
  .jsPlatform(
    scalaVersions = Seq(scala3),
    settings = Seq(libraryDependencies += "io.github.cquiroz" %% "scala-java-time" % V.scalaJavaTime)
  )

lazy val config = project
  .in(file("modules/config"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(values.jvm(scala3))
  .settings(
    name := "histmersion-config",
    description := "AppConfig and its readers: reference.conf, the user's file and the environment"
  )
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.kubuszok" %% "kindlings-sconfig-derivation" % V.kindlings,
      "org.ekrich" %% "sconfig" % V.sconfig
    )
  )

lazy val files = project
  .in(file("modules/files"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(values.jvm(scala3), api.jvm(scala3) % "compile->compile;test->test")
  .settings(
    name := "histmersion-files",
    description := "The local files client and server exchange or keep: bundles, merge files, the Markdown mirror"
  )
  .settings(commonDeps)
  .settings(libraryDependencies += "org.yaml" % "snakeyaml" % V.snakeyaml)

lazy val core = projectMatrix
  .in(file("modules/core"))
  .dependsOn(nonEmptyDerivation % Provided)
  .dependsOn(values, api % "compile->compile;test->test")
  // config for its tests only: the domain takes settings records of its own, which the server converts; the
  // upstreams' host rules for the tests that pass them in as the server does
  .dependsOn(config % "test->test", files, upstreams % "test->compile")
  .settings(
    name := "histmersion-core",
    description := "The domain logic the server and the store run on, with no database code"
  )
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % V.tapir,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % V.jsoniter,
      "com.kubuszok" %% "kindlings-jsoniter-derivation" % V.kindlings,
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

lazy val pluginApi = project
  .in(file("modules/plugin-api"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(newtype.jvm(scala3), problem.jvm(scala3), json.jvm(scala3))
  // sconfig, kindlings-sconfig-derivation and iron only (all in the plugin platform): no Histmersion class on a plugin's class path
  .settings(
    name := "histmersion-plugin-api",
    description := "The plugin SPI a plugin compiles against: interfaces, Secret, config readers, its own newtypes"
  )
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "org.ekrich" %% "sconfig" % V.sconfig,
      "com.kubuszok" %% "kindlings-sconfig-derivation" % V.kindlings,
      "io.github.iltotore" %% "iron" % V.iron
    )
  )

// The parsers and clients of the public upstreams (OpenAlex, Crossref, Unpaywall, CORE, DataCite, archive.org, Europe
// PMC, JATS, the landing-page rules, the catalogues' dating) over the SPI's HTTP: ONE copy, used by the first-party
// plugins and by the host. Plugin platform only (plugin-api, its JSON, sttp's model): it registers nothing.
lazy val upstreams = project
  .in(file("modules/upstreams"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(pluginApi)
  .settings(
    name := "histmersion-upstreams",
    description := "Parsers and clients of the public upstreams over the plugin SPI's HTTP, shared by plugins and host"
  )
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client4" %% "core" % V.sttp
    ),
    // the upstreams' captured answers, shared with the suites that read them through the host
    Test / unmanagedResourceDirectories ++= Seq(
      (ThisBuild / baseDirectory).value / "modules" / "api" / "src" / "test" / "resources",
      (ThisBuild / baseDirectory).value / "modules" / "core" / "src" / "test" / "resources"
    )
  )

lazy val pluginHost = project
  .in(file("modules/plugin-host"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(core.jvm(scala3) % "compile->compile;test->test", config, pluginApi, upstreams)
  .settings(
    name := "histmersion-plugin-host",
    description := "The host side of the plugin SPI, shared by the server and the testkit"
  )
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client4" %% "core" % V.sttp,
      "com.outr" %% "scribe" % V.scribe
    )
  )

lazy val pluginTestkit = project
  .in(file("modules/plugin-testkit"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(pluginHost)
  .settings(
    name := "histmersion-plugin-testkit",
    description := "Runs a plugin in its own tests as the server does, with live, recorded or replayed HTTP"
  )
  .settings(commonDeps)

lazy val pluginVaults = project
  .in(file("modules/plugin-vaults"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(pluginApi, pluginHost % Test)
  .settings(
    name := "histmersion-plugin-vaults",
    description := "The built-in secret vaults (KeePassXC, 1Password, Bitwarden), disabled by default"
  )
  .settings(commonDeps)

// Histmersion's own acquisition plugins (archive-org, oa-unpaywall, oa-core, oa-openalex, oa-landing, pmc, arxiv):
// plugin-api and the upstreams library alone, found by ServiceLoader like any plugin; in every image as the
// server's runtime dependency, each switched off by its `enabled`
lazy val pluginsFetchers = projectMatrix
  .in(file("modules/plugins/fetchers"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(upstreams, pluginTestkit % Test)
  .settings(
    name := "histmersion-plugins-fetchers",
    description := "Histmersion's own acquisition plugins: archive.org, the open-access indexes, PMC and arXiv"
  )
  .settings(commonDeps)
  .jvmPlatform(scalaVersions = Seq(scala3))

// Histmersion's own event catalogues (events-gvp, events-noaa, events-wikidata): plugin-api and the upstreams
// library alone, read by the host's one import loop as any catalogue; in every image, each switched off by its `enabled`
lazy val pluginsEvents = projectMatrix
  .in(file("modules/plugins/events"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(upstreams, pluginTestkit % Test)
  .settings(
    name := "histmersion-plugins-events",
    description := "Histmersion's own event catalogues: the GVP's eruptions, NOAA NCEI's hazards, Wikidata's events"
  )
  .settings(commonDeps)
  .settings(
    // the catalogues' captured answers, shared with the host's suites
    Test / unmanagedResourceDirectories += (ThisBuild / baseDirectory).value / "modules" / "core" / "src" / "test" / "resources"
  )
  .jvmPlatform(scalaVersions = Seq(scala3))

// Histmersion's own media collections (media-wikidata, media-depicts, media-met, media-cleveland, media-aic,
// media-loc, media-pleiades, media-opencontext, media-smithsonian, media-europeana): plugin-api and the upstreams
// library alone, run by the host's one collection import as any collection; in every image, each switched off by its
// `enabled` (the two keyed ones off by default)
lazy val pluginsMedia = projectMatrix
  .in(file("modules/plugins/media"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(upstreams, pluginTestkit % Test)
  .settings(
    name := "histmersion-plugins-media",
    description := "Histmersion's own media collections: Wikidata and Commons, museums, maps, excavation archives"
  )
  .settings(commonDeps)
  .settings(
    // the collections' captured answers, shared with the host's suites
    Test / unmanagedResourceDirectories += (ThisBuild / baseDirectory).value / "modules" / "server" / "src" / "test" / "resources"
  )
  .jvmPlatform(scalaVersions = Seq(scala3))

// Histmersion's own network gazetteers (gazetteer-cliopatria, gazetteer-awmc, gazetteer-chronontology): plugin-api and
// the upstreams library alone, run by the host's one gazetteer import as any source; in every image, each switched off
// by its `enabled`
lazy val pluginsGazetteers = projectMatrix
  .in(file("modules/plugins/gazetteers"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(upstreams, pluginTestkit % Test)
  .settings(
    name := "histmersion-plugins-gazetteers",
    description := "Histmersion's own network gazetteers: Seshat Cliopatria, the AWMC's regions, ChronOntology's periods"
  )
  .settings(commonDeps)
  .jvmPlatform(scalaVersions = Seq(scala3))

// Histmersion's own HTTP AI providers (ai-anthropic, ai-openai, ai-local): the Anthropic Messages API and the OpenAI
// chat-completions API over the SPI's AiHttp, plugin platform only (plugin-api, its JSON, sttp's model); the host's agent
// loop runs them as any plugin's provider; in every image, each switched off by its `enabled`
lazy val pluginsAiHttp = projectMatrix
  .in(file("modules/plugins/ai-http"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(pluginApi, pluginTestkit % Test)
  .settings(
    name := "histmersion-plugins-ai-http",
    description := "Histmersion's own HTTP AI providers: the Anthropic API, the OpenAI API and OpenAI-compatible servers"
  )
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client4" %% "core" % V.sttp
    )
  )
  .jvmPlatform(scalaVersions = Seq(scala3))

// Histmersion's own Calibre plugin (calibre): the local Calibre library as a source collection, read with calibredb
// (sqlite3 while the GUI holds it) and written back with it; plugin-api alone, in every image, switched off by
// its `enabled`. Its suites run a fake calibredb over fixture libraries only.
lazy val pluginsCalibre = projectMatrix
  .in(file("modules/plugins/calibre"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(pluginApi, pluginTestkit % Test)
  .settings(
    name := "histmersion-plugins-calibre",
    description := "Histmersion's own Calibre plugin: the local Calibre library as a source collection"
  )
  .settings(commonDeps)
  .jvmPlatform(scalaVersions = Seq(scala3))

lazy val pluginHello = project
  .in(file("modules/plugin-fixtures/hello"))
  .dependsOn(pluginApi)
  .settings(
    name := "histmersion-plugin-hello",
    description := "A test plugin jar for the plugin and relink tests",
    publish / skip := true
  )
  .settings(commonDeps)

lazy val pluginHelloAi = project
  .in(file("modules/plugin-fixtures/hello-ai"))
  .dependsOn(pluginApi)
  .settings(
    name := "histmersion-plugin-hello-ai",
    description := "A test AI-provider plugin jar: scripted, no network",
    publish / skip := true
  )
  .settings(commonDeps)

lazy val pluginHelloLib = project
  .in(file("modules/plugin-fixtures/hello-lib"))
  .settings(
    name := "histmersion-plugin-hello-lib",
    description := "A test library that the hello-deps test plugin depends on",
    version := "0.1.0",
    publish / skip := true
  )
  .settings(commonDeps)
  .settings(libraryDependencies += "io.github.iltotore" %% "iron" % V.iron)

val fixturesRepo = taskKey[File]("Write the test Maven repository with hello-lib and hello-deps")

lazy val pluginHelloDeps = project
  .in(file("modules/plugin-fixtures/hello-deps"))
  .dependsOn(pluginApi % Provided, pluginHelloLib)
  .settings(
    name := "histmersion-plugin-hello-deps",
    description := "A test plugin with a dependency of its own, published to a fixture Maven repository",
    version := "0.1.0",
    publish / skip := true
  )
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

lazy val console = project
  .in(file("modules/console"))
  .dependsOn(newtype.jvm(scala3))
  .settings(commonDeps)
  .settings(name := "histmersion-console", publish / skip := true)
  .settings(libraryDependencies += "io.github.iltotore" %% "iron" % V.iron)

lazy val pluginResolver = project
  .in(file("modules/plugin-resolver"))
  .dependsOn(console, problem.jvm(scala3))
  // run from the relink kit's tools/, never on the server's class path: coursier's jar carries native-image settings
  .settings(
    name := "histmersion-plugin-resolver",
    description := "Resolves user plugins' dependencies against the server's modules (coursier)",
    publish / skip := true
  )
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

lazy val chimneyIron = project
  .in(file("modules/chimney-iron"))
  // consumers depend on it % Provided; Chimney 2 and Iron 3.4 need Scala 3.10+ (3.9.0 fails here on a cyclic reference)
  .settings(
    name := "histmersion-chimney-iron",
    description := "A Chimney macro extension that converts an Iron newtype into another layer's"
  )
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "io.scalaland" %% "chimney" % V.chimney % Provided,
      // the extension matches Hearth's MacroCommonsScala3 (scala.quoted's Type and Expr): the Hearth Kindlings and
      // Chimney 2.1.1 are built on
      "com.kubuszok" %% "hearth" % V.hearth % Provided,
      "io.github.iltotore" %% "iron" % V.iron % Provided
    )
  )

lazy val chimneyIronTests = project
  .in(file("modules/chimney-iron-tests"))
  .dependsOn(chimneyIron % Provided, newtype.jvm(scala3))
  .settings(
    name := "histmersion-chimney-iron-tests",
    description := "chimney-iron used as a layer uses it, as a Provided dependency",
    publish / skip := true
  )
  .settings(commonDeps)
  .settings(
    libraryDependencies ++= Seq(
      "io.scalaland" %% "chimney" % V.chimney,
      "com.kubuszok" %% "hearth" % V.hearth, // the application's Hearth, as chimney-iron is built with it
      "io.github.iltotore" %% "iron" % V.iron
    )
  )

lazy val db = project
  .in(file("modules/db"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(config, core.jvm(scala3) % "compile->compile;test->test")
  // the store's rows (the model's vocabularies) into the api's records where a repository answers one: compile time only
  .dependsOn(chimneyIron % Provided)
  .settings(
    name := "histmersion-db",
    description := "The store: Magnum repositories over Postgres/PostGIS, Flyway migrations, the seed importer"
  )
  .settings(commonDeps)
  // one suite at a time per group: several rewrite the same tables, and side by side they can deadlock the pool
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
      "io.scalaland" %% "chimney" % V.chimney,
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
      "io.scalaland" %% "chimney" % V.chimney % Test,
      // the stored JSON shapes' samples (StoredShapesSuite): random values of the stored records
      "com.kubuszok" %% "kindlings-scalacheck-derivation" % V.kindlings % Test
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

/** Every module's sources and the files the source-reading suites compare them with (ContractsSuite, ArchitectureSuite,
  * GuidelineRatchetSuite): a digest that keys the server's cached test result, so a change in any module runs those
  * suites again rather than replaying a pass.
  */
val sourcesDigest = taskKey[String]("A digest of every module's sources and of the architecture and ratchet files")

/** Every module compiled with its tests: the SemanticDB GuidelineRatchetSuite reads is that of the last compile. */
val everyModuleCompiled = taskKey[Unit]("Compile every module of the build, main and test")

lazy val server = project
  .in(file("modules/server"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  .dependsOn(
    core.jvm(scala3),
    db % "compile->compile;test->test",
    pluginApi,
    upstreams,
    pluginHost,
    pluginVaults,
    console
  )
  // the api's newtypes (Pure) into the domain's, checked, and back, at the boundaries: compile time only
  .dependsOn(chimneyIron % Provided)
  // the first-party plugins: in every image, found by ServiceLoader at run time; no server code can name their classes
  .dependsOn(pluginsFetchers.jvm(scala3) % "runtime->compile", pluginsEvents.jvm(scala3) % "runtime->compile")
  .dependsOn(pluginsMedia.jvm(scala3) % "runtime->compile")
  .dependsOn(pluginsGazetteers.jvm(scala3) % "runtime->compile")
  .dependsOn(pluginsAiHttp.jvm(scala3) % "runtime->compile")
  .dependsOn(pluginsCalibre.jvm(scala3) % "runtime->compile;test->test")
  // the calibre commands' suite runs the CLI against a test server, as a person does
  .dependsOn(cli % "test->compile")
  .settings(
    name := "histmersion-server",
    description := "The server: MCP, REST and the web UI over the store; built as a native image",
    Compile / mainClass := Some("com.histmersion.server.histmersionServer")
  )
  .settings(commonDeps)
  // one suite at a time per group: a restore drops connections, and a booting server fails the running jobs
  .settings(dbTestGroups("server", groups = 5, heap = "2g"))
  .settings(
    everyModuleCompiled := Def.uncached {
      val _ = (Test / compile).all(ScopeFilter(inAggregates(LocalRootProject), inConfigurations(Test))).value
    },
    sourcesDigest := Def.uncached {
      val root = (ThisBuild / baseDirectory).value
      val files = (root / "modules").allPaths
        .filter(f => f.isFile && f.getName.endsWith(".scala") && !f.getPath.contains("/target/"))
        .get() ++
        (root / "architecture").allPaths.filter(_.isFile).get() ++
        Seq(root / "tools/refactor/GuidelineInventory.scala", root / "tools/refactor/guideline-ratchet.conf")
      val digest = java.security.MessageDigest.getInstance("SHA-256")
      files.filter(_.isFile).sortBy(_.getPath).foreach { f =>
        digest.update(f.getPath.getBytes("UTF-8"))
        digest.update(IO.readBytes(f))
      }
      digest.digest.map(b => f"${b & 0xff}%02x").mkString
    },
    Test / extraTestDigests += sbt.util.Digest.sha256Hash(s"sources=${sourcesDigest.value}".getBytes("UTF-8")),
    // `test` and `testOnly` both find the suites first: no suite starts before every module is compiled
    Test / definedTests := (Test / definedTests).dependsOn(everyModuleCompiled).value
  )
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
      // Runtime's: the first-party plugins are the server's runtime dependencies
      val entries = (Runtime / fullClasspath).value.map(a => conv.toPath(a.data).toFile)
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
      IO.write(out / "main-class", "com.histmersion.server.histmersionServer\n")
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
      // host it runs, the upstreams library it dates and parses with, values, api, files, core, and config, whose settings
      // (reference.conf) give it the server's User-Agent, timeouts and TLS trust. Their other dependencies are ordinary
      // Maven Central libraries their POMs name.
      def module(id: ModuleID, pom: File, jar: File, sources: File): Unit =
        writeMavenArtifact(repo, id, scalaBinaryVersion.value, pom, Seq("" -> jar, "sources" -> sources))
      module(
        (pluginTestkit / projectID).value,
        conv.toPath((pluginTestkit / makePom).value).toFile,
        conv.toPath((pluginTestkit / Compile / packageBin).value).toFile,
        conv.toPath((pluginTestkit / Compile / packageSrc).value).toFile
      )
      module(
        (upstreams / projectID).value,
        conv.toPath((upstreams / makePom).value).toFile,
        conv.toPath((upstreams / Compile / packageBin).value).toFile,
        conv.toPath((upstreams / Compile / packageSrc).value).toFile
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
      // the newtypes' base trait, which plugin-api's POM names
      module(
        (newtype.jvm(scala3) / projectID).value,
        conv.toPath((newtype.jvm(scala3) / makePom).value).toFile,
        conv.toPath((newtype.jvm(scala3) / Compile / packageBin).value).toFile,
        conv.toPath((newtype.jvm(scala3) / Compile / packageSrc).value).toFile
      )
      // the non-empty vector, which the closed Problem's POM names
      module(
        (nonEmpty.jvm(scala3) / projectID).value,
        conv.toPath((nonEmpty.jvm(scala3) / makePom).value).toFile,
        conv.toPath((nonEmpty.jvm(scala3) / Compile / packageBin).value).toFile,
        conv.toPath((nonEmpty.jvm(scala3) / Compile / packageSrc).value).toFile
      )
      // the closed Problem, which plugin-api's POM names
      module(
        (problem.jvm(scala3) / projectID).value,
        conv.toPath((problem.jvm(scala3) / makePom).value).toFile,
        conv.toPath((problem.jvm(scala3) / Compile / packageBin).value).toFile,
        conv.toPath((problem.jvm(scala3) / Compile / packageSrc).value).toFile
      )
      // JSON, which plugin-api's POM names
      module(
        (json.jvm(scala3) / projectID).value,
        conv.toPath((json.jvm(scala3) / makePom).value).toFile,
        conv.toPath((json.jvm(scala3) / Compile / packageBin).value).toFile,
        conv.toPath((json.jvm(scala3) / Compile / packageSrc).value).toFile
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
      // every resolved module of the shipped class paths with what pulled it in (ClasspathSuite). The update report
      // carries no callers under coursier, so they are read off the POMs: a library's from the cache beside its jar,
      // a module of ours from its makePom.
      val callers = (Test / resourceManaged).value / "dependency-callers.tsv"
      val conv = fileConverter.value
      val ours = makePom
        .all(ScopeFilter(inDependencies(ThisProject, includeRoot = false) || inDependencies(web, includeRoot = false)))
        .value
        .map(p => pomNeeds(conv.toPath(p).toFile))
        .toMap
      def rows(project: String, report: UpdateReport, conf: String): Seq[String] = {
        val modules = report.configuration(ConfigRef(conf)).toSeq.flatMap(_.modules).filterNot(_.evicted)
        val needs = modules.map { m =>
          val key = s"${m.module.organization}:${m.module.name}"
          val pom = m.artifacts.collectFirst {
            case (_, f) if f.getName.endsWith(".jar") =>
              new File(f.getParentFile, f.getName.stripSuffix(".jar") + ".pom")
          }
          key -> ours.getOrElse(key, pom.filter(_.isFile).fold(Seq.empty[String])(f => pomNeeds(f)._2))
        }
        modules.map { m =>
          val key = s"${m.module.organization}:${m.module.name}"
          val by = needs.collect { case (caller, ns) if ns.contains(key) => caller }.distinct.sorted.mkString(",")
          s"$project\t$conf\t${m.module.organization}\t${m.module.name}\t${m.module.revision}\t$by"
        }
      }
      IO.writeLines(
        callers,
        rows("server", (Runtime / update).value, "runtime") ++ rows("server", (Test / update).value, "test") ++
          rows("cli", (cli / Runtime / update).value, "runtime") ++
          rows("plugin-api", (pluginApi / Runtime / update).value, "runtime") ++
          rows("plugin-testkit", (pluginTestkit / Runtime / update).value, "runtime") ++
          rows("web", (web / Compile / update).value, "compile")
      )
      Seq(out, contract, built, callers)
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
      // embedded agents (docs/research-embedded-agents.md): the tool loop (the HTTP providers are the plugins of
      // plugins/ai-http). OFF by default (`agents.enabled`); the agent's tools are typed wrappers over Handlers,
      // in-process.
      "com.softwaremill.sttp.ai" %% "core" % V.sttpAi,
      // the real agent loop against a scripted model (its scalatest matchers are not used)
      "com.softwaremill.sttp.ai" %% "agent-testkit" % V.sttpAi % Test,
      // the OpenAPI document ContractSnapshotSuite compares with its snapshot
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % V.tapir % Test,
      // the JSON of the agents' tool and answer schemas (apispec's JSON Schema model) and of the OpenAPI document,
      // without circe; byte-for-byte what apispec's circe module wrote
      ("com.kubuszok" %% "kindlings-tapir-openapi-jsoniter" % V.kindlings)
        .exclude("com.softwaremill.sttp.tapir", "tapir-openapi-docs_3")
    )
  )

lazy val cli = project
  .in(file("modules/cli"))
  .dependsOn(nonEmptyDerivation.jvm(scala3) % Provided)
  // pluginTestkit and the Scala compiler are TEST dependencies only: the generated plugin projects are compiled against
  // the plugin API in `GeneratedPluginsCompileSuite`; the CLI itself still depends on neither core nor db
  .dependsOn(api.jvm(scala3), config, files, console, pluginTestkit % Test)
  .settings(
    name := "histmersion-cli",
    description := "The command line, a pure HTTP client of the server; built as a native image",
    Compile / mainClass := Some("com.histmersion.cli.histmersionCli")
  )
  .settings(commonDeps)
  .enablePlugins(NativeImage)
  .settings(nativeImageName := "histmersion-cli")
  .settings(
    libraryDependencies ++= Seq(
      "org.rogach" %% "scallop" % V.scallop,
      "com.outr" %% "scribe" % V.scribe,
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-client4" % V.tapir,
      "com.softwaremill.sttp.client4" %% "core" % V.sttp,
      "org.scala-lang" %% "scala3-compiler" % scala3 % Test
    )
  )
  .settings(
    // the class path the generated plugin projects are compiled against in `GeneratedPluginsCompileSuite`: the tests
    // are not forked, so the running JVM's own class path is sbt's
    Test / sourceGenerators += Def.task {
      val conv = fileConverter.value
      val out = (Test / sourceManaged).value / "com" / "histmersion" / "cli" / "PluginTestClasspath.scala"
      val entries = (Test / dependencyClasspath).value.map(a => conv.toPath(a.data).toString).distinct
      val src =
        s"""package com.histmersion.cli
           |
           |/** Generated by build.sbt — do not edit. */
           |object PluginTestClasspath:
           |  val entries: List[String] = List(
           |${entries.map(e => "    \"" + e.replace("\\", "\\\\").replace("\"", "\\\"") + "\"").mkString(",\n")}
           |  )
           |""".stripMargin
      IO.write(out, src)
      Seq(out)
    }.taskValue,
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

lazy val web = project
  .in(file("modules/web"))
  .dependsOn(nonEmptyDerivation.js(scala3) % Provided)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(api.js(scala3))
  .settings(
    name := "histmersion-web",
    description := "The web UI page in Scala.js (Laminar), over a client derived from the api endpoints"
  )
  .settings(commonDeps)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("com.histmersion.web.histmersionWeb"),
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
    (newtype.projectRefs ++ nonEmpty.projectRefs ++ nonEmptyDerivation.projectRefs ++ problem.projectRefs ++ json.projectRefs ++ values.projectRefs ++ api.projectRefs ++ core.projectRefs ++ pluginsFetchers.projectRefs ++
      pluginsEvents.projectRefs ++ pluginsMedia.projectRefs ++
      pluginsGazetteers.projectRefs ++ pluginsAiHttp.projectRefs ++ pluginsCalibre.projectRefs)*
  )
  .aggregate(
    config,
    files,
    pluginApi,
    upstreams,
    pluginHost,
    pluginTestkit,
    pluginVaults,
    pluginHello,
    pluginHelloAi,
    pluginHelloLib,
    pluginHelloDeps,
    pluginResolver,
    console,
    chimneyIron,
    chimneyIronTests,
    db,
    server,
    cli,
    web
  )
  .settings(
    name := "histmersion",
    description := "Histmersion: a geo-temporal research corpus with MCP, REST, a web UI and a CLI",
    publish / skip := true
  )
