package com.histmersion.cli

import java.nio.file.Path

/** The extension point a new plugin contributes to: its name on the command line, the suffix of the
  * contributed class's name, and what `histmersion plugin add` reports it provides. */
enum PluginKind(val name: String, val classSuffix: String, val provides: String):
  case SourceFetcher   extends PluginKind("source-fetcher", "Fetcher", "source-fetcher")
  case MediaCollection extends PluginKind("media-collection", "Collection", "media-collection")
  case EventCatalogue  extends PluginKind("event-catalogue", "Catalogue", "event-catalogue")
  case AiProvider      extends PluginKind("ai-provider", "Provider", "ai-provider")
  case SecretVault     extends PluginKind("secret-vault", "Vault", "secret-vault")

object PluginKind:
  val names: String = values.map(_.name).mkString(", ")
  def parse(raw: String): Either[String, PluginKind] =
    values.find(_.name == raw.trim).toRight(s"unknown plugin kind '$raw' (one of: $names)")

/** A plugin id: the settings key (`histmersion.plugins.<id>`) and the source of the project's names.
  * Stricter than the host's `[a-z0-9][a-z0-9-]*`: it must also make a Scala class name. */
final case class PluginId private (value: String):
  /** `my-mirror` → `MyMirror`. */
  def typeName: String = value.split('-').map(_.capitalize).mkString
  /** `my-mirror` → `mymirror`. */
  def packageName: String = value.replace("-", "")

object PluginId:
  private val Rule = "[a-z][a-z0-9]*(-[a-z0-9]+)*"
  def parse(raw: String): Either[String, PluginId] =
    Either.cond(raw.matches(Rule), PluginId(raw),
      s"'$raw' is not a plugin id: lower-case letters and digits in words joined by single dashes, starting with a letter ($Rule)")

/** A Scala package name the project's sources are written in. */
final case class ScalaPackage private (value: String):
  def path: String = value.replace('.', '/')

object ScalaPackage:
  private val Rule = "[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*"
  def parse(raw: String): Either[String, ScalaPackage] =
    Either.cond(raw.matches(Rule), ScalaPackage(raw), s"'$raw' is not a package name ($Rule)")

/** The Maven repository a plugin project resolves the plugin API and the plugin platform from: the
  * install's `maven/` as a `file:` URL, or another named with `--repo`. */
final case class PluginRepository private (url: String)

object PluginRepository:
  /** An `https://`, `http://` or `file:` URL as given, or a local directory as its `file:` URL; a
    * directory must exist. */
  def parse(raw: String, isDirectory: Path => Boolean): Either[String, PluginRepository] =
    val trimmed = raw.trim
    if trimmed.matches("(https?|file):.+") then Right(PluginRepository(trimmed))
    else
      val dir = Path.of(trimmed).toAbsolutePath.normalize
      Either.cond(isDirectory(dir), PluginRepository(dir.toUri.toString), s"no such directory: $dir")

/** Histmersion's own artifacts in that repository, at the version of the install this CLI belongs to:
  * the plugin API (a jar, its sources, its POM) and the plugin platform (a POM whose dependencies are
  * the libraries the server provides to plugins). */
object PluginArtifacts:
  val Api      = "histmersion-plugin-api"
  val Platform = "histmersion-plugin-platform"

  /** `group::name:version`, as scala-cli and `histmersion plugin add` write a Scala 3 artifact. */
  def coordinates(name: String): String = s"${PluginBuildVersions.organization}::$name:${PluginBuildVersions.histmersion}"

  /** A file of the artifact relative to the repository's root: `com/kubuszok/histmersion-plugin-api_3/<v>/…<suffix>`. */
  def path(name: String, suffix: String): String =
    val artifact = s"${name}_3"
    val version  = PluginBuildVersions.histmersion
    s"${PluginBuildVersions.organization.replace('.', '/')}/$artifact/$version/$artifact-$version$suffix"

/** One file of a new project: its path relative to the project directory, and its content. */
final case class TemplateFile(path: String, content: String)

/** What `histmersion plugin new` writes: a scala-cli project whose two Scala files are the plugin and its
  * tests, and the resources a plugin jar carries beside its classes. Pure: the caller writes the files. */
object PluginTemplate:

  final case class Request(id: PluginId, kind: PluginKind, pkg: ScalaPackage, repository: PluginRepository, agentNotes: Boolean)

  /** The version a new plugin is published under, as its `PluginInfo` states it too. */
  val FirstVersion = "0.1.0"

  /** The repository `scala-cli publish` writes the plugin to, relative to the project. */
  val PublishDir = "repo"

  val ServicesPath = "resources/META-INF/services/com.histmersion.plugin.Plugin"

  def defaultsPath(id: PluginId): String = s"resources/META-INF/histmersion/plugins/${id.value}.conf"

  def nativeMetadataPath(id: PluginId): String =
    s"resources/META-INF/native-image/histmersion-plugin/${id.value}/reachability-metadata.json"

  def pluginClass(id: PluginId): String =
    if id.typeName.endsWith("Plugin") then id.typeName else s"${id.typeName}Plugin"

  def contributionClass(id: PluginId, kind: PluginKind): String = s"${id.typeName}${kind.classSuffix}"

  def files(r: Request): List[TemplateFile] =
    val name = r.id.typeName
    val core = List(
      TemplateFile(s"$name.scala", fill(r, directives(r) + "\n" + header(r) + KindSources.main(r.kind))),
      TemplateFile(s"$name.test.scala", fill(r, testDirectives + "\n" + header(r) + KindSources.test(r.kind) + packagingSuite)),
      TemplateFile(".gitignore", s"/$PublishDir/\n/*.jar\n.scala-build/\n.bsp/\n"),
      TemplateFile(ServicesPath, s"${r.pkg.value}.${pluginClass(r.id)}\n"),
      TemplateFile(defaultsPath(r.id), fill(r, KindSources.defaults(r.kind))),
      TemplateFile(nativeMetadataPath(r.id), nativeMetadata(r))
    )
    if r.agentNotes then core ++ List(TemplateFile("AGENTS.md", fill(r, agentNotes(r))), TemplateFile("CLAUDE.md", "@AGENTS.md\n"))
    else core

  /** The build: the server's Scala; the plugin API and the platform from the repository, compile-only
    * (the server provides them, so the plugin's POM does not list them and its jar holds none of them);
    * the resources; what `scala-cli publish` names the plugin. A library of the plugin's own is a plain
    * `//> using dep`: its POM lists it, and `histmersion plugin add` installs it beside the plugin. */
  private def directives(r: Request): String =
    List(
      s"//> using scala ${PluginBuildVersions.scala}",
      s"//> using repository ${r.repository.url}",
      s"//> using compileOnly.dep ${PluginArtifacts.coordinates(PluginArtifacts.Api)}",
      s"//> using compileOnly.dep ${PluginArtifacts.coordinates(PluginArtifacts.Platform)}",
      "//> using resourceDir resources",
      s"//> using publish.organization ${r.pkg.value}",
      s"//> using publish.name ${r.id.value}",
      s"//> using publish.version $FirstVersion").mkString("", "\n", "\n")

  /** The tests run the plugin, so they need at run time what the server provides it with. */
  private def testDirectives: String =
    List(
      s"//> using test.dep org.scalameta::munit:${PluginBuildVersions.munit}",
      s"//> using test.dep ${PluginArtifacts.coordinates(PluginArtifacts.Api)}",
      s"//> using test.dep ${PluginArtifacts.coordinates(PluginArtifacts.Platform)}").mkString("", "\n", "\n")

  private def header(r: Request): String = s"package ${r.pkg.value}\n\n"

  private def fill(r: Request, text: String): String =
    List(
      "@id@"           -> r.id.value,
      "@Name@"         -> r.id.typeName,
      "@Plugin@"       -> pluginClass(r.id),
      "@Contribution@" -> contributionClass(r.id, r.kind),
      "@package@"      -> r.pkg.value,
      "@kind@"         -> r.kind.name,
      "@provides@"     -> r.kind.provides,
      "@metadata@"     -> nativeMetadataPath(r.id).stripPrefix("resources/"),
      "@repository@"   -> r.repository.url,
      "@api@"          -> PluginArtifacts.coordinates(PluginArtifacts.Api),
      "@platform@"     -> PluginArtifacts.coordinates(PluginArtifacts.Platform),
      "@apiSources@"   -> PluginArtifacts.path(PluginArtifacts.Api, "-sources.jar"),
      "@coordinates@"  -> s"${r.pkg.value}::${r.id.value}:$FirstVersion",
      "@publishDir@"   -> PublishDir
    ).foldLeft(text) { case (t, (k, v)) => t.replace(k, v) }

  /** The fields Scala 3 lazy vals keep their value in (`<name>$lzy<n>`): a native image reaches them
    * through a VarHandle, which needs them registered. The generated classes have one each: the
    * plugin's `configReader` and the derived reader in every config companion. The compiler numbers
    * the fields of one name through the source file, in the order they are written. */
  private def nativeMetadata(r: Request): String =
    val p = r.pkg.value
    val fields = (s"$p.${pluginClass(r.id)}" -> "configReader$lzy1") ::
      KindSources.configTypes(r.kind).zipWithIndex.map((t, i) => s"$p.${fill(r, t)}$$" -> s"derived$$ConfigReader$$lzy${i + 1}")
    val reflection = fields.map { (tpe, field) =>
      s"""    { "type": "$tpe", "fields": [ { "name": "$field" } ] }"""
    }.mkString(",\n")
    s"""{
       |  "reflection": [
       |$reflection
       |  ],
       |  "resources": [
       |    { "glob": "META-INF/histmersion/plugins/${r.id.value}.conf" }
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
      |    val defaults = ConfigFactory.parseString(resource(plugin.defaultsResource)).resolve().withoutPath("enabled")
      |    assert(plugin.configReader.from(defaults.root).isRight, plugin.configReader.from(defaults.root).toString)
      |
      |  test("the native-image metadata registers every lazy val and the defaults file"):
      |    val metadata = ConfigFactory.parseString(resource("@metadata@"))
      |    val listed = metadata.getConfigList("reflection").asScala.toList.flatMap(t =>
      |      t.getConfigList("fields").asScala.toList.map(f => s"${t.getString("type")}#${f.getString("name")}")).toSet
      |    assertEquals(lazyValFields.diff(listed), Set.empty[String])
      |    assert(metadata.getConfigList("resources").asScala.exists(_.getString("glob") == @Plugin@().defaultsResource))
      |
      |  private def lazyValFields: Set[String] =
      |    val location = Path.of(classOf[@Plugin@].getProtectionDomain.getCodeSource.getLocation.toURI)
      |    val root     = if Files.isDirectory(location) then location else FileSystems.newFileSystem(location).getPath("/")
      |    val classes  = Files.walk(root).iterator.asScala.map(p => root.relativize(p).toString)
      |      .filter(_.endsWith(".class")).map(_.stripSuffix(".class").replace('/', '.')).toList
      |    classes.flatMap(n => Class.forName(n, false, getClass.getClassLoader).getDeclaredFields.toList
      |      .map(_.getName).filter(_.matches(".*\\$lzy\\d+")).map(f => s"$n#$f")).toSet
      |""".stripMargin

  private def agentNotes(r: Request): String =
    """# @Name@: a Histmersion plugin
      |
      |A plugin for Histmersion, built with scala-cli. It adds a @kind@ (`@provides@:@id@`). These notes are
      |for coding agents and for people who work like them.
      |
      |## Files
      |
      |- `@Name@.scala`: the build directives (`//> using …`) and the plugin.
      |- `@Name@.test.scala`: its tests (munit), including the packaging checks.
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
      |- Both come from the Maven repository of the Histmersion install, `@repository@`
      |  (`histmersion plugin repo` prints its path), as compile-only dependencies:
      |  - `@api@`: everything is in the package `com.histmersion.plugin`. Start at `Plugin` and
      |    `PluginContext`.
      |  - `@platform@`: a POM whose dependencies are the libraries the server provides to plugins, at the
      |    versions it ships (sconfig and kindlings for settings, circe, cats, the sttp client, tapir, ox,
      |    iron). Use them freely: they are on the server's class path, so the plugin bundles none of them.
      |- Sources, with the Scaladoc that documents every type: `@apiSources@` in that repository
      |  (`histmersion plugin api-jar --sources` prints the full path). Read one with
      |  `unzip -p "<sources jar>" com/histmersion/plugin/Plugin.scala`; list them with `unzip -l`.
      |- cellar's project mode (`cellar get com.histmersion.plugin.PluginContext`, run here) works once your
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
      |- `PluginInfo(id, version, Spi.Version)`: the id is the settings key; keep it equal to the defaults
      |  file's name.
      |- `register` adds to the context's registries and returns `Right(())`, or `Left(why)`. A `Left` or an
      |  exception discards everything it added.
      |- Failures are values (`Left`, `Fetch.Miss`, `AiFailure.Error`), never exceptions.
      |- Use `ctx.http` for every request; never open a socket of your own.
      |- A credential is a `Secret` field in the config (a reference such as `env:NAME`); never log a value.
      |- No mutable state shared between calls: the server may call a fetcher from several threads at once.

      |## Build, test, publish, install
      |
      |```bash
      |scala-cli compile .
      |scala-cli test .
      |scala-cli --power publish . --publish-repository ./@publishDir@   # the jar and its POM, in Maven layout
      |histmersion plugin add @coordinates@ --repo ./@publishDir@         # shows what it provides and needs, then installs it
      |histmersion plugin rebuild             # relink the server with it (or: plugin rebuild --jvm)
      |```
      |
      |Bump `//> using publish.version` and the version in `PluginInfo` together for a new release.
      |
      |Turn it on in the user's `histmersion.conf`: `histmersion.plugins.@id@ { enabled = true }`. Check it
      |with `histmersion about` (the plugin is listed as registered).
      |
      |## Licence
      |
      |Histmersion and its plugin API are licensed under the AGPL-3.0, and so is a plugin built on the API.
      |""".stripMargin
