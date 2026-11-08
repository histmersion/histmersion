package com.histmersion
package server.plugins

import scala.annotation.tailrec
import com.histmersion.pluginhost.{HostServices, PluginHost}

import com.histmersion.config.AppConfig
import com.histmersion.api.plugins.{PluginDescription, PluginFile, PluginJar, PluginSet, PluginState}
import com.histmersion.util.plugins.PluginKit
import com.histmersion.plugin.{Plugin, Spi}
import com.histmersion.util.plugins.{HostProvided, LegacyPlugins, PluginSetResource}
import org.ekrich.config.{ConfigFactory, ConfigUtil}

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.ServiceLoader
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.control.NonFatal
import com.histmersion.util.console.Console
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Sha256
import com.histmersion.pluginhost.DiskLocalFiles
import com.histmersion.pluginhost.SystemProcesses

/** The JVM-side tool of the relink kit (docs/research-plugins.md §7.4): run by `bin/histmersion-relink` and the JVM
  * launcher with the kit's jars on the class path, as [[pluginTool]] — never part of the server's native image (nothing
  * the server's entry point reaches calls it).
  *
  * {{{
  *   describe [--lib <dir>] <jar>…           the plugins in each jar, as JSON (PluginJar)
  *   check [--lib <dir>] <kit dir> <jar>…    refuse what cannot be linked: no plugin, a foreign SPI, an id
  *                                           taken, a class the kit, a dependency or another plugin
  *                                           already has
  *   plugin-set [--lib <dir>] <out.jar> <record.json> <mode> <kit dir> <jar>…
  *                                           the plugin-set jar for the image class path, and its record
  *   kit-manifest <kit dir> <version> <commit|-> <graalvm>
  *                                           kit.json and SHA256SUMS over every file of the kit
  * }}}
  *
  * `--lib` names the plugins directory's `lib/`: the plugins' own dependencies (its `classpath` file lists them), which
  * the plugins are loaded with.
  *
  * Exit codes: 0 done, 1 usage, 2 refused (`check`), 3 failed. Describing a jar INSTANTIATES its providers and
  * registers each with its defaults (enabled, nothing else set; no HTTP — the host's client refuses every request), to
  * learn what it contributes and on what terms: that runs the plugin's code, in this short-lived JVM, as installing it
  * will.
  */
@main def pluginTool(args: String*): Unit = sys.exit(RelinkPlugins.exitCode(args.toList.map(RelinkPlugins.Argument(_)))(using Console.system).value)

object RelinkPlugins:
  type Argument = Argument.T
  object Argument extends Newtype[String, Pure]
  type ClassName = ClassName.T
  object ClassName extends Newtype[String, Pure]
  type ExitCode = ExitCode.T
  object ExitCode extends Newtype[Int, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]

  extension (id: PluginDescription.Id) private def nonEmpty: Boolean = id.value.nonEmpty

  private val SpiVersion: PluginSet.SpiVersion = PluginSet.SpiVersion(Spi.Version)

  private val Usage =
    """usage: pluginTool describe [--lib <dir>] <jar>…
      |       pluginTool check [--lib <dir>] <kit dir> <jar>…
      |       pluginTool plugin-set [--lib <dir>] <out.jar> <record.json> <native|jvm> <kit dir> <jar>…
      |       pluginTool kit-manifest <kit dir> <version> <commit|-> <graalvm version>""".stripMargin

  def exitCode(args: List[Argument])(using console: Console): ExitCode = try run(args)(using console)
    catch
      case NonFatal(e) =>
        console.errorLine(s"pluginTool: ${e.getClass.getSimpleName}: ${e.getMessage}")
        ExitCode(3)

  private def run(args: List[Argument])(using console: Console): ExitCode = args match
    case cmd :: flag :: lib :: rest if flag.value == "--lib" => run(cmd, rest, dependencyJars(Path.of(lib.value)))(using console)
    case cmd :: rest                   => run(cmd, rest, Nil)(using console)
    case Nil                           => console.errorLine(Usage); ExitCode(1)

  private def run(cmd: Argument, args: List[Argument], deps: List[Path])(using console: Console): ExitCode =
    val loader = withDependencies(deps, getClass.getClassLoader)
    (cmd.value, args.map(_.value)) match
      case ("describe", jars) if jars.nonEmpty =>
        console.writeLine(PluginKit.renderJars(jars.map(j => describe(Path.of(j), loader))))
        ExitCode(0)
      case ("check", kit :: jars) =>
        val (lines, problems) = check(Path.of(kit), jars.map(Path.of(_)), deps, loader)
        lines.foreach(l => console.writeLine(l))
        problems.foreach(p => console.writeLine(s"refused: $p"))
        if problems.isEmpty then ExitCode(0) else ExitCode(2)
      case ("plugin-set", out :: record :: mode :: kit :: jars) if Set("native", "jvm")(mode) =>
        val described = jars.map(j => describe(Path.of(j), loader))
        val label = kitLabel(Path.of(kit))
        val set = PluginKit.pluginSet(
          described,
          SpiVersion,
          PluginSet.Mode(mode),
          label,
          Some(PluginSet.LinkedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString)),
          linkedFiles(jars.map(Path.of(_)), deps)
        )
        writePluginSet(Path.of(out), set)
        val _ = Files.writeString(Path.of(record), PluginKit.renderSet(set) + "\n")
        console.writeLine(set.digest)
        ExitCode(0)
      case ("kit-manifest", kit :: version :: commit :: graalvm :: Nil) =>
        val m = kitManifest(
          Path.of(kit),
          PluginKit.KitManifest.Version(version),
          Option(commit).filter(c => c != "-" && c.nonEmpty).map(PluginKit.KitManifest.Commit(_)),
          PluginKit.KitManifest.Graalvm(graalvm)
        )
        val _ = Files.writeString(Path.of(kit).resolve("kit.json"), PluginKit.renderKit(m) + "\n")
        val _ = Files.writeString(Path.of(kit).resolve("SHA256SUMS"), PluginKit.sha256sums(m.files))
        console.writeLine(s"kit ${m.label}: ${m.files.size} files, GraalVM ${m.graalvm}, plugin SPI ${m.spiVersion}")
        ExitCode(0)
      case _ =>
        console.errorLine(Usage)
        ExitCode(1)

  // ── the plugins' dependencies ─────────────────────────────────────────────────────────────────

  /** The files a plugin set is linked with besides its jars, as `histmersion plugin status` reads them: the POM beside
    * each jar (`x-1.0.jar` → `x-1.0.pom`, when there is one) and each dependency jar as `lib/<name>`, with their
    * SHA-256.
    */
  def linkedFiles(jars: List[Path], deps: List[Path]): List[(PluginFile.File, Sha256)] =
    val poms = jars
      .map(j => j.resolveSibling(j.getFileName.toString.stripSuffix(".jar") + ".pom"))
      .filter(Files.isRegularFile(_))
    poms.map(p => PluginFile.File(p.getFileName.toString) -> sha256(p)) ++ deps
      .filter(Files.isRegularFile(_))
      .map(d => PluginFile.File(s"lib/${d.getFileName}") -> sha256(d))

  /** The jars `lib/classpath` lists, in its order; none when there is no such file. */
  def dependencyJars(lib: Path): List[Path] =
    val listing = lib.resolve("classpath")
    if !Files.isRegularFile(listing) then Nil
    else Files.readAllLines(listing, UTF_8).asScala.toList.map(_.trim).filter(_.nonEmpty).map(lib.resolve)

  private def withDependencies(deps: List[Path], parent: ClassLoader): ClassLoader =
    if deps.isEmpty then parent else URLClassLoader(deps.map(_.toAbsolutePath.toUri.toURL).toArray, parent)

  // ── describe ──────────────────────────────────────────────────────────────────────────────────

  def sha256(p: Path): Sha256 = Sha256(digestHex(p))

  private def kitSha256(p: Path): com.histmersion.Sha256 = com.histmersion.Sha256.applyUnsafe(digestHex(p))

  private def digestHex(p: Path): String =
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(p)
    try
      val buf = new Array[Byte](1 << 16)
      Iterator.continually(in.read(buf)).takeWhile(_ >= 0).foreach(n => md.update(buf, 0, n))
    finally in.close()
    md.digest().map(b => f"$b%02x").mkString

  /** The plugins `jar` provides — only providers loaded FROM it, not from `parent`. */
  def describe(jar: Path, parent: ClassLoader): PluginJar =
    val abs = jar.toAbsolutePath.normalize
    val loader = URLClassLoader(Array(abs.toUri.toURL), parent)
    val plugins =
      try
        ServiceLoader
          .load(classOf[Plugin], loader)
          .stream()
          .iterator()
          .asScala
          .toList
          .filter(p => fromJar(p.`type`(), abs))
          .map { prov =>
            val instance =
              try Right(prov.get())
              catch
                case e: LinkageError => Left(e)
                case NonFatal(e)     => Left(e)
            instance.flatMap(PluginHost.described) match
              case Right(p) => dryRun(p)
              case Left(e)  =>
                unloadable(s"${prov.`type`().getName}: ${PluginHost.failure(e).value}")
          }
      catch
        case e: java.util.ServiceConfigurationError =>
          List(unloadable(PluginHost.failure(e).value))
        case e: LinkageError => List(unloadable(PluginHost.failure(e).value))
    PluginJar(PluginJar.File(abs.getFileName.toString), sha256(abs), plugins)

  private def unloadable(error: String): PluginDescription =
    PluginDescription(
      PluginDescription.Id(""),
      PluginDescription.Version(""),
      PluginDescription.SpiVersion(0),
      error = Some(PluginDescription.Error(error))
    )

  private def fromJar(c: Class[?], jar: Path): Boolean =
    Try(Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toAbsolutePath.normalize == jar).getOrElse(false)

  private val NoHttp: Plugin.Http = new Plugin.Http:
    def get(url: Plugin.Http.GetUrl, headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]) = Left(Plugin.Http.GetResultPart1("no HTTP while a plugin is being described"))

  /** Register `p` alone, enabled, with its defaults, into throw-away registries: what it provides and the access its
    * source fetchers declare.
    */
  def dryRun(p: Plugin): PluginDescription =
    val info = p.info
    val user = Try(ConfigFactory.parseString(s"histmersion.plugins.${ConfigUtil.quoteString(info.id.value)}.enabled = true"))
      .getOrElse(ConfigFactory.empty("plugin-tool"))
    val g = PluginHost.build(
      List(p),
      user,
      ConfigFactory.empty("plugin-tool"),
      HostServices(() => NoHttp, ConfigFactory.empty("plugin-tool"), (_, _, _) => (), files = new DiskLocalFiles, processes = new SystemProcesses)
    )
    val st = g.report.plugins.find(_.id.value == info.id.value)
    val access =
      g.registries.sourceFetchers.values
        .map(f => PluginDescription.Access(f.access.toString.toLowerCase(java.util.Locale.ROOT)))
        .toList
        .distinct
    PluginDescription(
      PluginDescription.Id(info.id.value),
      PluginDescription.Version(info.version.value),
      PluginDescription.SpiVersion(info.spiVersion.value),
      st.map(_.provides).getOrElse(Nil),
      access,
      st.filter(s => s.state.value == PluginState.Failed.wire.value || s.state.value == PluginState.Refused.wire.value).flatMap(_.error)
    )

  // ── check ─────────────────────────────────────────────────────────────────────────────────────

  /** The class files of a jar (`module-info` and multi-release copies left out: neither is a duplicate). */
  def classesOf(jar: Path): Set[ClassName] =
    val z = ZipFile(jar.toFile)
    try
      z.entries()
        .asScala
        .map(_.getName)
        .filter(n => n.endsWith(".class") && !n.startsWith("META-INF/") && !n.endsWith("module-info.class"))
        .map(ClassName(_))
        .toSet
    finally z.close()

  /** Pure: the classes each plugin jar shares with the kit or with an earlier plugin jar. The image has one class
    * loader: a second copy of a class is a conflict, never a choice (§7.1).
    */
  def duplicates(kit: Set[ClassName], plugins: List[(PluginJar.File, Set[ClassName])]): List[PluginKit.Problem] =
    @tailrec def loop(rest: List[(PluginJar.File, Set[ClassName])], seen: Set[(ClassName, String)], acc: List[PluginKit.Problem]): List[PluginKit.Problem] =
      rest match
        case Nil                     => acc
        case (name, classes) :: tail =>
          val owners = seen.toMap
          val dups = classes.toList.sortBy(_.value).filter(owners.contains)
          val msg = if dups.isEmpty then Nil
          else
            val by = dups.groupBy(owners).toList.sortBy(_._1).map { (o, cs) =>
              s"${cs.size} with $o (${cs.take(3).map(_.value.stripSuffix(".class").replace('/', '.')).mkString(", ")}" +
                (if cs.size > 3 then ", …)" else ")")
            }
            List(
              PluginKit.Problem(
                s"$name duplicates classes already on the class path: ${by.mkString("; ")} — one class loader holds one copy " +
                  "of a class: depend on the library in the plugin's POM instead of copying its classes into the jar"
              )
            )
          loop(tail, seen ++ classes.map(_ -> name.value), acc ++ msg)
    loop(plugins, kit.map(_ -> "the kit"), Nil)

  /** Everything that refuses linking these plugin jars, with their dependency jars `deps`, into this kit, and a line
    * per plugin found.
    */
  def check(kit: Path, jars: List[Path], deps: List[Path], loader: ClassLoader): (List[Note], List[PluginKit.Problem]) =
    val described = jars.map(j => describe(j, loader))
    val taken = hostIds(loader)
    // an id provided twice among the plugin jars, or already in the binary
    @tailrec def idRefusals(rest: List[PluginJar], t: Map[PluginDescription.Id, String], acc: List[PluginKit.Problem]): List[PluginKit.Problem] =
      rest match
        case Nil       => acc
        case j :: tail =>
          idRefusals(
            tail,
            t ++ j.plugins.filter(_.id.nonEmpty).map(_.id -> j.file.value),
            acc ++ PluginKit.refusals(j, SpiVersion, t)
          )
    val idProblems = idRefusals(described, taken, Nil)
    val kitJars = kitClassPath(kit)
    val kitClasses = kitJars.flatMap(classesOf).toSet
    val dups = duplicates(kitClasses, (deps ++ jars).map(j => PluginJar.File(j.getFileName.toString) -> classesOf(j)))
    val lines = described.flatMap(j =>
      j.plugins.map(p =>
        Note(s"plugin: ${p.id} ${p.version} (SPI ${p.spiVersion}) in ${j.file} · access ${
            if p.access.isEmpty then "none declared" else p.access.mkString(", ")
          }" +
          (if p.provides.isEmpty then "" else s" · provides ${p.provides.mkString(", ")}") +
          p.error.fold("")(e => s" · with its defaults alone: $e"))
      )
    )
    (lines, idProblems ++ dups)

  /** The ids the binary itself has: the plugins on the kit's class path (the first-party ones among them) and the ones
    * the host supplies itself.
    */
  def hostIds(loader: ClassLoader): Map[PluginDescription.Id, String] =
    val discovered = PluginHost.discover(loader)._1.map(p => PluginDescription.Id(p.info.id.value))
    val supplied = HostProvided.Ids.map(id => PluginDescription.Id(id.value))
    (discovered ++ supplied).map(_ -> "this Histmersion (a first-party plugin)").toMap ++
      LegacyPlugins.All.map(s =>
        PluginDescription.Id(s.id.value) -> s"this Histmersion (a former first-party plugin, now ${s.into.mkString(", ")})"
      )

  /** The plugin-set jar written to `out`, through a temporary file beside it and a rename. */
  def writePluginSet(out: Path, set: PluginSet): Unit =
    val tmp = out.resolveSibling(out.getFileName.toString + ".tmp")
    val stream = Files.newOutputStream(tmp)
    try PluginSetResource.writeJar(stream, set)
    finally stream.close()
    val _ = Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

  private def kitClassPath(kit: Path): List[Path] =
    val order = kit.resolve("classpath")
    if Files.isRegularFile(order) then
      Files
        .readAllLines(order, UTF_8)
        .asScala
        .toList
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(n => kit.resolve("lib").resolve(n))
    else Nil

  // ── the kit ───────────────────────────────────────────────────────────────────────────────────

  private def kitLabel(kit: Path): Option[PluginSet.Kit] =
    val f = kit.resolve("kit.json")
    if Files.isRegularFile(f) then PluginKit.parseKit(Files.readString(f)).toOption.map(m => PluginSet.Kit(m.label)) else None

  /** The manifest over every file under `kit` (the manifests themselves excluded). */
  def kitManifest(kit: Path, version: PluginKit.KitManifest.Version, commit: Option[PluginKit.KitManifest.Commit], graalvm: PluginKit.KitManifest.Graalvm): PluginKit.KitManifest =
    val files = Files
      .walk(kit)
      .iterator()
      .asScala
      .toList
      .filter(Files.isRegularFile(_))
      .map(p => kit.relativize(p).toString.replace('\\', '/'))
      .filterNot(Set("kit.json", "SHA256SUMS"))
      .sorted
      .map(rel => PluginKit.KitFile(PluginKit.KitFile.File(rel), kitSha256(kit.resolve(rel))))
    val main = PluginKit.KitManifest.MainClass(
      Try(Files.readString(kit.resolve("main-class")).trim).getOrElse("com.histmersion.server.histmersionServer")
    )
    PluginKit.KitManifest(PluginKit.KitFormat, version, commit, graalvm, SpiVersion, main, files)
