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

/** The plugin API jar a project compiles against, and its sources jar for the IDE and the Scaladoc. */
final case class ApiJars(jar: Path, sources: Option[Path])

/** One file of a new project: its path relative to the project directory, and its content. */
final case class TemplateFile(path: String, content: String)

/** What `histmersion plugin new` writes: a scala-cli project whose two Scala files are the plugin and its
  * tests, and the resources a plugin jar carries beside its classes. Pure: the caller writes the files. */
object PluginTemplate:

  final case class Request(id: PluginId, kind: PluginKind, pkg: ScalaPackage, api: ApiJars, agentNotes: Boolean)

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
      TemplateFile(ServicesPath, s"${r.pkg.value}.${pluginClass(r.id)}\n"),
      TemplateFile(defaultsPath(r.id), fill(r, KindSources.defaults(r.kind))),
      TemplateFile(nativeMetadataPath(r.id), nativeMetadata(r))
    )
    if r.agentNotes then core ++ List(TemplateFile("AGENTS.md", fill(r, agentNotes(r))), TemplateFile("CLAUDE.md", "@AGENTS.md\n"))
    else core

  /** A directive value in double quotes, as scala-cli reads it: a path may hold spaces. */
  private def quoted(p: Path): String = "\"" + p.toAbsolutePath.normalize.toString.replace("\"", "\\\"") + "\""

  private def directives(r: Request): String =
    (List(
      s"//> using scala ${PluginBuildVersions.scala}",
      s"//> using jar ${quoted(r.api.jar)}") ++
      r.api.sources.map(s => s"//> using sourceJar ${quoted(s)}") ++
      List(
        s"//> using dep org.ekrich::sconfig:${PluginBuildVersions.sconfig}",
        s"//> using dep com.kubuszok::kindlings-sconfig-derivation:${PluginBuildVersions.kindlings}",
        "//> using resourceDir resources")).mkString("", "\n", "\n")

  private def testDirectives: String = s"//> using test.dep org.scalameta::munit:${PluginBuildVersions.munit}\n"

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
      "@apiJar@"       -> r.api.jar.toAbsolutePath.normalize.toString,
      "@apiSources@"   -> r.api.sources.fold("(no sources jar was found beside the API jar)")(_.toAbsolutePath.normalize.toString)
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
      |## The plugin API
      |
      |- Jar: `@apiJar@`
      |- Sources, with the Scaladoc that documents every type: `@apiSources@`
      |- Everything is in the package `com.histmersion.plugin`. Start at `Plugin` and `PluginContext`.
      |- Read a type's source and Scaladoc: `unzip -p "<sources jar>" com/histmersion/plugin/Plugin.scala`
      |  (list them with `unzip -l`). Signatures alone: `javap -cp "<jar>" com.histmersion.plugin.PluginContext`.
      |- cellar's project mode (`cellar get com.histmersion.plugin.PluginContext`, run here) works once your
      |  cellar reads Scala 3.10 TASTy; older releases stop with "TASTy signature has wrong version".
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
      |- The server provides the plugin API, sconfig and kindlings. Any other library must be shaded into the
      |  jar (scala-cli cannot do this; prefer the JDK's own classes).
      |
      |## Build, test, install
      |
      |```bash
      |scala-cli compile .
      |scala-cli test .
      |scala-cli --power package --library . -o @id@.jar -f
      |histmersion plugin add ./@id@.jar      # shows what it provides, then installs it
      |histmersion plugin rebuild             # relink the server with it (or: plugin rebuild --jvm)
      |```
      |
      |Turn it on in the user's `histmersion.conf`: `histmersion.plugins.@id@ { enabled = true }`. Check it
      |with `histmersion about` (the plugin is listed as registered).
      |
      |## Licence
      |
      |Histmersion and its plugin API are licensed under the AGPL-3.0, and so is a plugin built on the API.
      |""".stripMargin
