package com.histmersion
package cli

import io.github.iltotore.iron.*

import java.nio.file.Path
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** What `histmersion plugin new` writes: a scala-cli project whose build is `project.scala`, whose two Scala files are
  * the plugin and its tests, and the resources a plugin jar carries beside its classes. Pure: the caller writes the
  * files.
  */
object PluginTemplate:
  type ClassName = ClassName.T
  object ClassName extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  final case class Request(
      id: PluginId,
      kind: PluginKind,
      pkg: ScalaPackage,
      repository: TemplateRepository,
      agentNotes: Request.AgentNotes
  )

  object Request:
    type AgentNotes = AgentNotes.T
    object AgentNotes extends Newtype[Boolean, Pure]

  /** The version a new plugin is published under, as its `Plugin.Info` states it too. */
  val FirstVersion = "0.1.0"

  /** The repository `scala-cli publish` writes the plugin to, relative to the project. */
  val PublishDir = "repo"

  /** The build: every `//> using` directive of the project, main and test. */
  val ProjectFile = "project.scala"

  /** Files only the tests read (fixtures): on the test class path, never in the jar. */
  val TestResourceDir = "test-resources"

  /** The system property the tests get the project's directory in: the testkit resolves a relative recordings directory
    * against it, wherever `scala-cli test` was started.
    */
  val ProjectProperty = "histmersion.plugin.project"

  val ServicesPath = "resources/META-INF/services/com.histmersion.plugin.Plugin"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def defaultsPath(id: PluginId): TemplateFile.Path =
    TemplateFile.Path(shown"resources/META-INF/histmersion/plugins/$id.conf")

  /** GraalVM's convention, `META-INF/native-image/<group>/<artifact>/`: the published organization and name, so no two
    * jars' metadata files share a path.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def nativeMetadataPath(id: PluginId, pkg: ScalaPackage): TemplateFile.Path =
    TemplateFile.Path(shown"resources/META-INF/native-image/$pkg/$id/reachability-metadata.json")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pluginClass(id: PluginId): ClassName =
    val typeName = id.typeName.value
    ClassName(if typeName.endsWith("Plugin") then typeName else s"${typeName}Plugin")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def contributionClass(id: PluginId, kind: PluginKind): ClassName =
    ClassName(s"${id.typeName}${kind.classSuffix}")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def files(r: Request): List[TemplateFile] =
    val name = r.id.typeName
    val core = List(
      TemplateFile(TemplateFile.Path(ProjectFile), TemplateFile.Content(fill(r, directives(r)))),
      TemplateFile(
        TemplateFile.Path(shown"$name.scala"),
        TemplateFile.Content(fill(r, header(r) + KindSources.main(r.kind)))
      ),
      TemplateFile(
        TemplateFile.Path(shown"$name.test.scala"),
        TemplateFile.Content(fill(r, header(r) + KindSources.test(r.kind) + packagingSuite))
      ),
      TemplateFile(TemplateFile.Path("README.md"), TemplateFile.Content(fill(r, readme))),
      TemplateFile(
        TemplateFile.Path(".gitignore"),
        TemplateFile.Content(s"/$PublishDir/\n/*.jar\n.scala-build/\n.bsp/\n/target/\n")
      ),
      TemplateFile(TemplateFile.Path(ServicesPath), TemplateFile.Content(shown"${r.pkg}.${pluginClass(r.id)}\n")),
      TemplateFile(defaultsPath(r.id), TemplateFile.Content(fill(r, KindSources.defaults(r.kind)))),
      TemplateFile(nativeMetadataPath(r.id, r.pkg), TemplateFile.Content(nativeMetadata(r)))
    )
    if r.agentNotes.value then
      core ++ List(
        TemplateFile(TemplateFile.Path("AGENTS.md"), TemplateFile.Content(fill(r, agentNotes(r)))),
        TemplateFile(TemplateFile.Path("CLAUDE.md"), TemplateFile.Content("@AGENTS.md\n"))
      )
    else core

  /** The build, in one file for every source: the server's Scala; the plugin API and the platform from the repository,
    * compile-only (the server provides them, so the plugin's POM does not list them and its jar holds none of them);
    * the resources; what `scala-cli publish` names the plugin. Then the tests: they run the plugin, so they need at run
    * time what the server provides it with (a compile-only dependency is not on the test class path); the testkit loads
    * and runs it as the server does, and brings the plugin API with it. A library of the plugin's own is a plain
    * `//> using dep`: its POM lists it, and `histmersion plugin add` installs it beside the plugin.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def directives(r: Request): String =
    s"""// The build of the @Name@ plugin: every //> using directive of the project, for the plugin and its
       |// tests. Keep them all in this file when you add sources (scala-cli reads directives from any
       |// file, but one place is one place to change them).
       |//
       |// ${repositoryNote(r.repository)}
       |// Their version is the Histmersion version; scala-cli has no variables, so it is written on each
       |// of their four lines below: change all four to build against another Histmersion.
       |
       |//> using scala ${PluginBuildVersions.scala}
       |//> using repository ${r.repository.directive}
       |//> using compileOnly.dep @api@
       |//> using compileOnly.dep @platform@
       |//> using resourceDir resources
       |
       |//> using publish.organization ${r.pkg.value}
       |//> using publish.name ${r.id.value}
       |//> using publish.version $FirstVersion
       |// no doc jar: scaladoc cannot follow links into the compile-only API, and warns for each
       |//> using publish.doc false
       |
       |//> using test.dep org.scalameta::munit:${PluginBuildVersions.munit}
       |//> using test.dep @platform@
       |//> using test.dep @testkit@
       |//> using test.resourceDir $TestResourceDir
       |//> using test.javaProp $ProjectProperty=$${.}
       |""".stripMargin

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def repositoryNote(repository: TemplateRepository): String = repository match
    case TemplateRepository.MavenLocal =>
      "The plugin API, the platform and the testkit come from the local Maven repository (m2Local), where\n" +
        "// `histmersion plugin repo --m2` puts the install's."
    case TemplateRepository.Remote(_) =>
      "The plugin API, the platform and the testkit come from the Maven repository named below."

  /** The GraalVM CE release of sdkman's name for a build: `25.5` for `25.5.5.0+3-graalce`. Pure. */
  private[cli] def graalvmRelease(sdkman: String): String = sdkman.split('.').take(2).mkString(".")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def header(r: Request): String = shown"package ${r.pkg}\n\n"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def fill(r: Request, text: String): String =
    List(
      "@id@" -> r.id.value,
      "@Name@" -> r.id.typeName.value,
      "@Plugin@" -> pluginClass(r.id).value,
      "@Contribution@" -> contributionClass(r.id, r.kind).value,
      "@package@" -> r.pkg.value,
      "@kind@" -> r.kind.name.value,
      "@provides@" -> r.kind.provides.value,
      "@metadata@" -> nativeMetadataPath(r.id, r.pkg).value.stripPrefix("resources/"),
      "@repository@" -> r.repository.directive,
      "@api@" -> PluginArtifacts.coordinates(PluginArtifacts.CoordinatesName(PluginArtifacts.Api)).value,
      "@platform@" -> PluginArtifacts.coordinates(PluginArtifacts.CoordinatesName(PluginArtifacts.Platform)).value,
      "@testkit@" -> PluginArtifacts.coordinates(PluginArtifacts.CoordinatesName(PluginArtifacts.Testkit)).value,
      "@apiSources@" -> PluginArtifacts.path(PluginArtifacts.PathName(PluginArtifacts.Api), "-sources.jar").value,
      "@coordinates@" -> shown"${r.pkg}::${r.id}:$FirstVersion",
      "@publishDir@" -> PublishDir,
      "@projectProperty@" -> ProjectProperty,
      "@graalvm@" -> PluginBuildVersions.graalvm,
      "@graalvmRelease@" -> graalvmRelease(PluginBuildVersions.graalvm)
    ).foldLeft(text) { case (t, (k, v)) => t.replace(k, v) }

  /** The fields Scala 3 lazy vals keep their value in (`<name>$lzy<n>`): a native image reaches them through a
    * VarHandle, which needs them registered. The generated classes have one each: the plugin's `configReader` and the
    * derived reader in every config companion. The compiler numbers the fields of one name through the source file, in
    * the order they are written.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def nativeMetadata(r: Request): String =
    val p = r.pkg.value
    val fields = (s"$p.${pluginClass(r.id)}" -> "configReader$lzy1") ::
      KindSources
        .configTypes(r.kind)
        .zipWithIndex
        .map((t, i) => s"$p.${fill(r, t)}$$" -> s"derived$$ConfigReader$$lzy${i + 1}")
    val reflection = fields
      .map { (tpe, field) =>
        s"""    { "type": "$tpe", "fields": [ { "name": "$field" } ] }"""
      }
      .mkString(",\n")
    shown"""{
       |  "reflection": [
       |$reflection
       |  ],
       |  "resources": [
       |    { "glob": "META-INF/histmersion/plugins/${r.id}.conf" }
       |  ]
       |}
       |""".stripMargin

  private val packagingSuite: String =
    """
      |/** What the server reads from the jar besides the classes: the ServiceLoader file that names the
      |  * plugin, its defaults, and the native-image metadata a relinked server needs. */
      |class @Name@PackagingSuite extends munit.FunSuite:
      |  import java.nio.file.{FileSystems, Files, Path}
      |  import org.ekrich.config.ConfigFactory
      |  import scala.jdk.CollectionConverters.*
      |
      |  private def resource(path: String): String =
      |    Option(getClass.getClassLoader.getResourceAsStream(path))
      |      .map(in => String(in.readAllBytes, java.nio.charset.StandardCharsets.UTF_8))
      |      .getOrElse(fail(s"no resource $path"))
      |
      |  test("the services file names the plugin class"):
      |    val named = resource("META-INF/services/com.histmersion.plugin.Plugin").linesIterator.map(_.trim).filter(_.nonEmpty).toList
      |    assertEquals(named, List(classOf[@Plugin@].getName))
      |
      |  test("the defaults decode with the plugin's own reader"):
      |    val plugin   = @Plugin@()
      |    val defaults = ConfigFactory.parseString(resource(plugin.defaultsResource.value)).resolve().withoutPath("enabled")
      |    assert(plugin.configReader.from(defaults.root).isRight, plugin.configReader.from(defaults.root).toString)
      |
      |  test("the native-image metadata registers every lazy val and the defaults file"):
      |    val metadata = ConfigFactory.parseString(resource("@metadata@"))
      |    val listed = metadata.getConfigList("reflection").asScala.toList.flatMap(t =>
      |      t.getConfigList("fields").asScala.toList.map(f => s"${t.getString("type")}#${f.getString("name")}")).toSet
      |    assertEquals(lazyValFields.diff(listed), Set.empty[String])
      |    assert(metadata.getConfigList("resources").asScala.exists(_.getString("glob") == @Plugin@().defaultsResource.value))
      |
      |  private def lazyValFields: Set[String] =
      |    val location = Path.of(classOf[@Plugin@].getProtectionDomain.getCodeSource.getLocation.toURI)
      |    val root     = if Files.isDirectory(location) then location else FileSystems.newFileSystem(location).getPath("/")
      |    val classes  = Files.walk(root).iterator.asScala.map(p => root.relativize(p).toString)
      |      .filter(_.endsWith(".class")).map(_.stripSuffix(".class").replace('/', '.')).toList
      |    classes.flatMap(n => Class.forName(n, false, getClass.getClassLoader).getDeclaredFields.toList
      |      .map(_.getName).filter(_.matches(".*\\$lzy\\d+")).map(f => s"$n#$f")).toSet
      |""".stripMargin

  /** How to build and test the plugin: unit tests, the live check, recording and replaying. */
  private val readme: String =
    """# @Name@
      |
      |A Histmersion plugin: a @kind@ (`@provides@:@id@`), built with [scala-cli](https://scala-cli.virtuslab.org)
      |against the plugin API of a Histmersion install.
      |
      |## Build
      |
      |The build is `project.scala`: every `//> using` directive of the project, for the plugin and for its
      |tests (the `test.*` ones). Keep them all there when you add source files. The plugin API, the plugin
      |platform (the libraries the server provides, at its versions) and the plugin testkit come from
      |`@repository@`; with `m2Local`, the local Maven repository (`~/.m2/repository`), which
      |`histmersion plugin repo --m2` fills from the install, so the project names no path of your computer.
      |All three are at the version of your Histmersion: to build against another one, change the version
      |on their four lines.
      |
      || path | what it is |
      ||---|---|
      || `project.scala` | the build |
      || `@Name@.scala` · `@Name@.test.scala` | the plugin · its tests |
      || `resources/` | what the jar carries besides the classes: the services file, the defaults, the native-image metadata |
      || `test-resources/` | files only the tests read (fixtures): on the test class path, never in the jar |
      || `recordings/` | the live check's recorded answers, replayed offline |
      |
      |## Install it from this directory
      |
      |You need an installed Histmersion (its `histmersion` CLI and the server), scala-cli, and a JDK: use the one
      |Histmersion is built with, GraalVM CE @graalvmRelease@ (`sdk install java @graalvm@`, then point `JAVA_HOME`
      |at it). The Histmersion source repository is not needed.
      |
      |1. **Build and test**, offline:
      |
      |   ```bash
      |   scala-cli test .
      |   ```
      |
      |2. **Publish** the jar, its sources and its POM (which lists the plugin's own libraries) into `./@publishDir@`,
      |   a Maven repository on disk:
      |
      |   ```bash
      |   scala-cli --power publish . --publish-repository ./@publishDir@
      |   ```
      |
      |3. **Install it.** This shows the id, version, SPI, what it registers and its dependencies, then copies the
      |   jar and its POM into the plugins directory and installs the POM's dependencies beside it (`--yes` skips
      |   the question):
      |
      |   ```bash
      |   histmersion plugin add @coordinates@ --repo ./@publishDir@
      |   ```
      |
      |4. **Enable it** in `histmersion.conf` (once; the settings and their defaults are in
      |   `resources/META-INF/histmersion/plugins/@id@.conf`):
      |
      |   ```hocon
      |   histmersion.plugins.@id@.enabled = true
      |   ```
      |
      |5. **Relink the server with it.** A native server runs only the plugins it was linked with, so it is linked
      |   again with the plugins directory, checked and restarted; on failure the previous binary comes back. The
      |   link peaks near 13.5 GB of memory. Let running jobs finish first:
      |
      |   ```bash
      |   histmersion agent autopilot drain      # wait until `histmersion agent autopilot status` says DRAINED
      |   histmersion plugin rebuild             # or: plugin rebuild --jvm — no relink, the server runs on the JVM
      |   histmersion agent autopilot undrain
      |   ```
      |
      |6. **Check** with `histmersion plugin status` (the running server lists the plugin's jar), and in the server
      |   log, which names the plugin as it registers.
      |
      |**Updating.** Raise `publish.version` in `project.scala` and the version in `Plugin.Info` together, then repeat
      |steps 1–3 with that version, and step 5. `histmersion plugin status` says REBUILD NEEDED whenever the plugins
      |directory differs from what the running server was linked with.
      |
      |**Removing.** `histmersion plugin rm @id@ --rebuild` takes it out of the plugins directory and relinks; set
      |`enabled = false` too, or the server warns that the setting configures no plugin.
      |
      |## Testing without a server
      |
      |The tests use the Histmersion plugin testkit (`@testkit@`). `PluginHarness.load("@id@", config)`
      |loads the plugin from the test class path exactly as the server does: it finds it through
      |`META-INF/services`, layers the config over `resources/META-INF/histmersion/plugins/@id@.conf`,
      |type-checks it, resolves its secrets and calls `register`. A setting the server would refuse is
      |refused here with the server's own message. The harness then runs what the plugin registered as the
      |server runs it (`fetch`, `searchMedia`, `readCatalogue`, `aiTurn`, `resolveSecret`) and reports what
      |the server would make of the answers: a "PDF" that is not one is no copy, a licence the server does
      |not allow is refused, a record whose dating runs backwards is not stored.
      |
      |Every request goes through the server's own HTTP client: its User-Agent, timeouts and TLS trust, its
      |handling of `Retry-After`, a bot challenge ending the request. Where it then goes is the `HttpMode`:
      |
      || mode | the wire |
      ||---|---|
      || `HttpMode.Stub(Map(url -> StubAnswer…))` | canned answers (the unit tests); any other URL is a 404 |
      || `HttpMode.Live` | the real site |
      || `HttpMode.Record(dir)` | the real site, and every exchange written to `dir` |
      || `HttpMode.Replay(dir)` | no network: the answers recorded in `dir`; a request with no recording fails the test, naming its URL |
      |
      |## The live check, recorded and replayed
      |
      |The `…LiveSuite` (where the kind has one) gives the plugin real settings and real search data, and
      |checks that it finds (and, for a fetcher, downloads into `target/fetched/`) what it should. Edit its
      |`config` and its case to one the real site has. It picks its mode with `HttpMode.fromEnv`:
      |
      |1. `HISTMERSION_PLUGIN_HTTP=live|record|replay`, when set, decides;
      |2. otherwise, if `recordings/<case>/` holds a recording, it is replayed, offline (in CI too);
      |3. otherwise it runs against the real site only with `HISTMERSION_PLUGIN_LIVE=1`, and is skipped
      |   (with the reason) without it.
      |
      |`recordings/<case>` is found in this directory wherever `scala-cli test` runs from: `project.scala` gives
      |the tests the project's directory (`test.javaProp @projectProperty@`), and the testkit resolves the
      |path against it. A skipped live check prints `SKIPPED …` with the reason and how to run it (munit
      |itself only says "skipped", and the run still passes).
      |
      |So: run it live until it passes, then record it once and commit the recording:
      |
      |```bash
      |HISTMERSION_PLUGIN_LIVE=1 scala-cli test .              # against the site
      |HISTMERSION_PLUGIN_HTTP=record scala-cli test .         # the same, keeping the answers in recordings/
      |scala-cli test .                                        # from now on: replayed, offline
      |```
      |
      |Be polite while recording: a site's terms and `robots.txt` may ask for a pause between requests,
      |and the plugin should keep to it (a replay skips the waits the server's client makes, but not the
      |plugin's own).
      |
      |A recording is plain files: `NNN-<host>.http` per exchange (method, URL, status and the headers that
      |matter: content type, redirects, `Retry-After`, a challenge marker) and its body beside it. They are
      |meant to be read in a diff. What is never written: `Authorization`, cookies and every other header,
      |the value of a secret the test knows (`{secret}` stands in for it, in URLs and text bodies alike),
      |and the contact e-mail of a `histmersion.conf` (the testkit never sends it).
      |
      |**Trim before committing.** A body is a file you may edit: cut a long search answer down to the
      |records the test reads, and never commit a copyrighted file (a book, an article, a picture). For a
      |PDF the server only checks that `%PDF` is in its first KiB and that it is longer than a KiB, so
      |
      |```bash
      |head -c 2048 recordings/known-work/004-files.example.pdf > /tmp/t && mv /tmp/t recordings/known-work/004-files.example.pdf
      |```
      |
      |keeps the replay passing. Lines starting with `#` in a `.http` file are notes (say what you trimmed).
      |
      |## Secrets
      |
      |A credential in the settings is a reference (`env:PORTAL_TOKEN`, `keepassxc:Portal/token`). In a test
      |give its value through `PluginHarness.load(…, secrets = Map("keepassxc:Portal/token" -> "…"))` or the
      |environment; no real vault or keychain is ever asked.
      |
      |## Licence
      |
      |Histmersion and its plugin API are licensed under the AGPL-3.0, and so is a plugin built on the API.
      |""".stripMargin

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def agentNotes(r: Request): String =
    """# @Name@: a Histmersion plugin
      |
      |A plugin for Histmersion, built with scala-cli. It adds a @kind@ (`@provides@:@id@`). These notes are
      |for coding agents and for people who work like them.
      |
      |## Files
      |
      |- `project.scala`: the build, every `//> using …` directive of the project, the tests' (`test.*`) too.
      |  Put the directives of any file you add here, not in that file.
      |- `@Name@.scala`: the plugin. More sources may sit beside it (`*.test.scala` for tests).
      |- `@Name@.test.scala`: its tests (munit, with the Histmersion plugin testkit, which loads and runs the
      |  plugin as the server does), including the packaging checks and, for a kind that talks to a site, a
      |  live check that records and replays (README.md).
      |- `test-resources/`: fixtures the tests read from the class path; never in the jar.
      |- `recordings/`: the live checks' recorded answers, replayed offline.
      |- `resources/META-INF/services/com.histmersion.plugin.Plugin`: names the plugin class, so that Java's
      |  ServiceLoader finds it. Rename the class and you must rename it here too (a test checks it).
      |- `resources/META-INF/histmersion/plugins/@id@.conf`: the plugin's settings and their defaults (HOCON,
      |  keys relative to `histmersion.plugins.@id@`). Every setting the plugin reads needs a default here.
      |- `resources/@metadata@`: what a natively linked server must be told about: every
      |  lazy val field (a test lists the missing ones) and the defaults file. A class you load by name, or a
      |  resource you read, goes here too.
      |
      |## The plugin API and the platform
      |
      |- Both come from `@repository@` (`m2Local` is `~/.m2/repository`, filled from the install by
      |  `histmersion plugin repo --m2`; `histmersion plugin repo` prints the install's own), as compile-only
      |  dependencies:
      |  - `@api@`: everything is in the package `com.histmersion.plugin`. Start at `Plugin` and
      |    `Plugin.Context`. The values it exchanges have types of their own (`Year`, `Period`, `Latitude`,
      |    `Longitude`, `Doi`, `WikidataId`, `Licence`; Iron newtypes): a literal is checked when it compiles
      |    (`Doi("10.1234/abc")`, `Year(-1600)`), a value read at run time is parsed (`Doi.option(raw)`,
      |    `Latitude.either(raw)`), and `.value` takes the plain value out.
      |  - `@platform@`: a POM whose dependencies are the libraries the server provides to plugins, at the
      |    versions it ships (sconfig and kindlings for settings, jsoniter-scala and kindlings for JSON, the sttp client, tapir, ox,
      |    iron). Use them freely: they are on the server's class path, so the plugin bundles none of them.
      |- Sources, with the Scaladoc that documents every type: `@apiSources@` in that repository
      |  (`histmersion plugin api-jar --sources` prints the full path). Read one with
      |  `unzip -p "<sources jar>" com/histmersion/plugin/Plugin.scala`; list them with `unzip -l`.
      |- cellar's project mode (`cellar get com.histmersion.plugin.Plugin$Context`, run here) works once your
      |  cellar reads Scala 3.10 TASTy; older releases stop with "TASTy signature has wrong version".
      |
      |## Other libraries
      |
      |- Add one with `//> using dep group::artifact:version`. The published POM lists it, and
      |  `histmersion plugin add` downloads it (and what it depends on) and installs it beside the plugin.
      |  Never copy a library's classes into the plugin jar: the server refuses a class it already has.
      |- A library the server already ships is not installed again; the server's copy is used. When the
      |  plugin needs a NEWER version of one than the server ships, `plugin add` refuses it and names both
      |  versions: build against the platform's version instead.
      |- A library that loads classes by name, or reads resources, may need its own native-image metadata;
      |  the relink's self-check tells you.
      |
      |## Rules the server enforces
      |
      |- One service type, `Plugin`: a public class with a no-argument constructor that does nothing. The
      |  work starts in `register`, which the server calls only when the plugin is enabled.
      |- `Plugin.Info(id, version, Spi.Version)`: the id is the settings key; keep it equal to the defaults
      |  file's name.
      |- `register` adds to the context's registries and returns `Right(())`, or `Left(why)`. A `Left` or an
      |  exception discards everything it added.
      |- Failures are values (`Left` with a `Problem`, `Fetch.Miss`), never exceptions.
      |- Use `ctx.http` for every request; never open a socket of your own.
      |- A credential is a `Secret` field in the config (a reference such as `env:NAME`); never log a value.
      |- No mutable state shared between calls: the server may call a fetcher from several threads at once.

      |## Build, test, publish, install
      |
      |```bash
      |scala-cli fmt .                        # scalafmt over the sources (a .scalafmt.conf, if any, sets the style)
      |scala-cli compile .
      |scala-cli test .                       # offline; HISTMERSION_PLUGIN_LIVE=1 also runs the live checks
      |scala-cli --power publish . --publish-repository ./@publishDir@   # the jar and its POM, in Maven layout
      |histmersion plugin add @coordinates@ --repo ./@publishDir@         # shows what it provides and needs, then installs it
      |histmersion plugin rebuild             # relink the server with it (or: plugin rebuild --jvm)
      |```
      |
      |Bump `//> using publish.version` and the version in `Plugin.Info` together for a new release. A native
      |server needs `plugin rebuild` after every `plugin add` or `plugin rm` (README.md, "Install it").
      |
      |Turn it on in the user's `histmersion.conf`: `histmersion.plugins.@id@ { enabled = true }`. Check it
      |with `histmersion about` (the plugin is listed as registered).
      |
      |## Licence
      |
      |Histmersion and its plugin API are licensed under the AGPL-3.0, and so is a plugin built on the API.
      |""".stripMargin
