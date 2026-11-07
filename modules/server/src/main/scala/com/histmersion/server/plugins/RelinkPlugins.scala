package com.histmersion.server.plugins

import scala.annotation.tailrec
import com.histmersion.pluginhost.{HostServices, PluginHost}

import com.histmersion.config.AppConfig
import com.histmersion.api.plugins.{PluginDescription, PluginJar, PluginSet, PluginState}
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
@main def pluginTool(args: String*): Unit = sys.exit(RelinkPlugins.exitCode(args.toList)(using Console.system))

object RelinkPlugins:
  type Argument = Argument.T
  object Argument extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassName = ClassName.T
  object ClassName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Command = Command.T
  object Command extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Duplicate = Duplicate.T
  object Duplicate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExitCode = ExitCode.T
  object ExitCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderContent = HeaderContent.T
  object HeaderContent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderName = HeaderName.T
  object HeaderName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JarName = JarName.T
  object JarName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KitLabel = KitLabel.T
  object KitLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedPath = LinkedPath.T
  object LinkedPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type GetUrl = GetUrl.T
  object GetUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Usage =
    """usage: pluginTool describe [--lib <dir>] <jar>…
      |       pluginTool check [--lib <dir>] <kit dir> <jar>…
      |       pluginTool plugin-set [--lib <dir>] <out.jar> <record.json> <native|jvm> <kit dir> <jar>…
      |       pluginTool kit-manifest <kit dir> <version> <commit|-> <graalvm version>""".stripMargin

  def exitCode(args: List[com.histmersion.server.plugins.RelinkPlugins.Argument])(using console: Console): com.histmersion.server.plugins.RelinkPlugins.ExitCode = try run(args)(using console)
    catch
      case NonFatal(e) =>
        console.errorLine(s"pluginTool: ${e.getClass.getSimpleName}: ${e.getMessage}")
        3

  private def run(args: List[com.histmersion.server.plugins.RelinkPlugins.Argument])(using console: Console): com.histmersion.server.plugins.RelinkPlugins.ExitCode = args match
    case cmd :: "--lib" :: lib :: rest => run(cmd, rest, dependencyJars(Path.of(lib)))(using console)
    case cmd :: rest                   => run(cmd, rest, Nil)(using console)
    case Nil                           => console.errorLine(Usage); 1

  private def run(cmd: com.histmersion.server.plugins.RelinkPlugins.Command, args: List[com.histmersion.server.plugins.RelinkPlugins.Argument], deps: List[Path])(using console: Console): com.histmersion.server.plugins.RelinkPlugins.ExitCode =
    val loader = withDependencies(deps, getClass.getClassLoader)
    (cmd, args) match
      case ("describe", jars) if jars.nonEmpty =>
        console.writeLine(PluginKit.renderJars(jars.map(j => describe(Path.of(j), loader))))
        0
      case ("check", kit :: jars) =>
        val (lines, problems) = check(Path.of(kit), jars.map(Path.of(_)), deps, loader)
        lines.foreach(l => console.writeLine(l))
        problems.foreach(p => console.writeLine(s"refused: $p"))
        if problems.isEmpty then 0 else 2
      case ("plugin-set", out :: record :: mode :: kit :: jars) if Set("native", "jvm")(mode) =>
        val described = jars.map(j => describe(Path.of(j), loader))
        val label = kitLabel(Path.of(kit))
        val set = PluginKit.pluginSet(
          described,
          Spi.Version,
          mode,
          label,
          Some(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString),
          linkedFiles(jars.map(Path.of(_)), deps)
        )
        writePluginSet(Path.of(out), set)
        val _ = Files.writeString(Path.of(record), PluginKit.renderSet(set) + "\n")
        console.writeLine(set.digest)
        0
      case ("kit-manifest", kit :: version :: commit :: graalvm :: Nil) =>
        val m = kitManifest(Path.of(kit), version, Option(commit).filter(c => c != "-" && c.nonEmpty), graalvm)
        val _ = Files.writeString(Path.of(kit).resolve("kit.json"), PluginKit.renderKit(m) + "\n")
        val _ = Files.writeString(Path.of(kit).resolve("SHA256SUMS"), PluginKit.sha256sums(m.files))
        console.writeLine(s"kit ${m.label}: ${m.files.size} files, GraalVM ${m.graalvm}, plugin SPI ${m.spiVersion}")
        0
      case _ =>
        console.errorLine(Usage)
        1

  // ── the plugins' dependencies ─────────────────────────────────────────────────────────────────

  /** The files a plugin set is linked with besides its jars, as `histmersion plugin status` reads them: the POM beside
    * each jar (`x-1.0.jar` → `x-1.0.pom`, when there is one) and each dependency jar as `lib/<name>`, with their
    * SHA-256.
    */
  def linkedFiles(jars: List[Path], deps: List[Path]): List[(com.histmersion.server.plugins.RelinkPlugins.JarName, com.histmersion.server.plugins.RelinkPlugins.LinkedPath)] =
    val poms = jars
      .map(j => j.resolveSibling(j.getFileName.toString.stripSuffix(".jar") + ".pom"))
      .filter(Files.isRegularFile(_))
    poms.map(p => p.getFileName.toString -> sha256(p)) ++ deps
      .filter(Files.isRegularFile(_))
      .map(d => s"lib/${d.getFileName}" -> sha256(d))

  /** The jars `lib/classpath` lists, in its order; none when there is no such file. */
  def dependencyJars(lib: Path): List[Path] =
    val listing = lib.resolve("classpath")
    if !Files.isRegularFile(listing) then Nil
    else Files.readAllLines(listing, UTF_8).asScala.toList.map(_.trim).filter(_.nonEmpty).map(lib.resolve)

  private def withDependencies(deps: List[Path], parent: ClassLoader): ClassLoader =
    if deps.isEmpty then parent else URLClassLoader(deps.map(_.toAbsolutePath.toUri.toURL).toArray, parent)

  // ── describe ──────────────────────────────────────────────────────────────────────────────────

  def sha256(p: Path): com.histmersion.Sha256 =
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
                PluginDescription("", "", 0, error = Some(s"${prov.`type`().getName}: ${PluginHost.failure(e)}"))
          }
      catch
        case e: java.util.ServiceConfigurationError =>
          List(PluginDescription("", "", 0, error = Some(PluginHost.failure(e))))
        case e: LinkageError => List(PluginDescription("", "", 0, error = Some(PluginHost.failure(e))))
    PluginJar(abs.getFileName.toString, sha256(abs), plugins)

  private def fromJar(c: Class[?], jar: Path): Boolean =
    Try(Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toAbsolutePath.normalize == jar).getOrElse(false)

  private val NoHttp: Plugin.Http = new Plugin.Http:
    def get(url: com.histmersion.server.plugins.RelinkPlugins.GetUrl, headers: Map[com.histmersion.server.plugins.RelinkPlugins.HeaderName, com.histmersion.server.plugins.RelinkPlugins.HeaderContent]) = Left("no HTTP while a plugin is being described")

  /** Register `p` alone, enabled, with its defaults, into throw-away registries: what it provides and the access its
    * source fetchers declare.
    */
  def dryRun(p: Plugin): PluginDescription =
    val info = p.info
    val user = Try(ConfigFactory.parseString(s"histmersion.plugins.${ConfigUtil.quoteString(info.id)}.enabled = true"))
      .getOrElse(ConfigFactory.empty("plugin-tool"))
    val g = PluginHost.build(
      List(p),
      user,
      ConfigFactory.empty("plugin-tool"),
      HostServices(() => NoHttp, ConfigFactory.empty("plugin-tool"), (_, _, _) => (), files = new com.histmersion.pluginhost.DiskLocalFiles, processes = new com.histmersion.pluginhost.SystemProcesses)
    )
    val st = g.report.plugins.find(_.id == info.id)
    val access =
      g.registries.sourceFetchers.values.map(_.access.toString.toLowerCase(java.util.Locale.ROOT)).toList.distinct
    PluginDescription(
      info.id,
      info.version,
      info.spiVersion,
      st.map(_.provides).getOrElse(Nil),
      access,
      st.filter(s => s.state == PluginState.Failed.wire || s.state == PluginState.Refused.wire).flatMap(_.error)
    )

  // ── check ─────────────────────────────────────────────────────────────────────────────────────

  /** The class files of a jar (`module-info` and multi-release copies left out: neither is a duplicate). */
  def classesOf(jar: Path): Set[com.histmersion.server.plugins.RelinkPlugins.ClassName] =
    val z = ZipFile(jar.toFile)
    try
      z.entries()
        .asScala
        .map(_.getName)
        .filter(n => n.endsWith(".class") && !n.startsWith("META-INF/") && !n.endsWith("module-info.class"))
        .toSet
    finally z.close()

  /** Pure: the classes each plugin jar shares with the kit or with an earlier plugin jar. The image has one class
    * loader: a second copy of a class is a conflict, never a choice (§7.1).
    */
  def duplicates(kit: Set[com.histmersion.server.plugins.RelinkPlugins.KitLabel], plugins: List[(com.histmersion.util.plugins.Plugin.Id, Set[com.histmersion.server.plugins.RelinkPlugins.ClassName])]): List[com.histmersion.server.plugins.RelinkPlugins.Duplicate] =
    @tailrec def loop(rest: List[(com.histmersion.util.plugins.Plugin.Id, Set[com.histmersion.server.plugins.RelinkPlugins.ClassName])], seen: Set[(com.histmersion.server.plugins.RelinkPlugins.ClassName, com.histmersion.util.plugins.Plugin.Id)], acc: List[com.histmersion.server.plugins.RelinkPlugins.Duplicate]): List[com.histmersion.server.plugins.RelinkPlugins.Duplicate] =
      rest match
        case Nil                     => acc
        case (name, classes) :: tail =>
          val owners = seen.toMap
          val dups = classes.toList.sorted.filter(owners.contains)
          val msg = if dups.isEmpty then Nil
          else
            val by = dups.groupBy(owners).toList.sortBy(_._1).map { (o, cs) =>
              s"${cs.size} with $o (${cs.take(3).map(_.stripSuffix(".class").replace('/', '.')).mkString(", ")}" +
                (if cs.size > 3 then ", …)" else ")")
            }
            List(
              s"$name duplicates classes already on the class path: ${by.mkString("; ")} — one class loader holds one copy " +
                "of a class: depend on the library in the plugin's POM instead of copying its classes into the jar"
            )
          loop(tail, seen ++ classes.map(_ -> name), acc ++ msg)
    loop(plugins, kit.map(_ -> "the kit"), Nil)

  /** Everything that refuses linking these plugin jars, with their dependency jars `deps`, into this kit, and a line
    * per plugin found.
    */
  def check(kit: Path, jars: List[Path], deps: List[Path], loader: ClassLoader): (List[com.histmersion.server.plugins.RelinkPlugins.Problem], List[com.histmersion.server.plugins.RelinkPlugins.Note]) =
    val described = jars.map(j => describe(j, loader))
    val taken = hostIds(loader)
    // an id provided twice among the plugin jars, or already in the binary
    @tailrec def idRefusals(rest: List[PluginJar], t: Map[com.histmersion.util.plugins.Plugin.Id, com.histmersion.server.plugins.RelinkPlugins.JarName], acc: List[com.histmersion.server.plugins.RelinkPlugins.Problem]): List[com.histmersion.server.plugins.RelinkPlugins.Problem] =
      rest match
        case Nil       => acc
        case j :: tail =>
          idRefusals(
            tail,
            t ++ j.plugins.filter(_.id.nonEmpty).map(_.id -> j.file),
            acc ++ PluginKit.refusals(j, Spi.Version, t)
          )
    val idProblems = idRefusals(described, taken, Nil)
    val kitJars = kitClassPath(kit)
    val kitClasses = kitJars.flatMap(classesOf).toSet
    val dups = duplicates(kitClasses, (deps ++ jars).map(j => j.getFileName.toString -> classesOf(j)))
    val lines = described.flatMap(j =>
      j.plugins.map(p =>
        s"plugin: ${p.id} ${p.version} (SPI ${p.spiVersion}) in ${j.file} · access ${
            if p.access.isEmpty then "none declared" else p.access.mkString(", ")
          }" +
          (if p.provides.isEmpty then "" else s" · provides ${p.provides.mkString(", ")}") +
          p.error.fold("")(e => s" · with its defaults alone: $e")
      )
    )
    (lines, idProblems ++ dups)

  /** The ids the binary itself has: the plugins on the kit's class path (the first-party ones among them) and the ones
    * the host supplies itself.
    */
  def hostIds(loader: ClassLoader): Map[com.histmersion.util.plugins.Plugin.Id, com.histmersion.server.plugins.RelinkPlugins.ClassName] =
    val discovered = PluginHost.discover(loader)._1.map(_.info.id)
    val supplied = HostProvided.Ids
    (discovered ++ supplied).map(_ -> "this Histmersion (a first-party plugin)").toMap ++
      LegacyPlugins.All.map(s =>
        s.id -> s"this Histmersion (a former first-party plugin, now ${s.into.mkString(", ")})"
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

  private def kitLabel(kit: Path): Option[com.histmersion.server.plugins.RelinkPlugins.KitLabel] =
    val f = kit.resolve("kit.json")
    if Files.isRegularFile(f) then PluginKit.parseKit(Files.readString(f)).toOption.map(_.label) else None

  /** The manifest over every file under `kit` (the manifests themselves excluded). */
  def kitManifest(kit: Path, version: com.histmersion.util.plugins.PluginKit.KitManifest.Version, commit: Option[com.histmersion.util.plugins.PluginKit.KitManifest.Commit], graalvm: com.histmersion.util.plugins.PluginKit.KitManifest.Graalvm): PluginKit.KitManifest =
    val files = Files
      .walk(kit)
      .iterator()
      .asScala
      .toList
      .filter(Files.isRegularFile(_))
      .map(p => kit.relativize(p).toString.replace('\\', '/'))
      .filterNot(Set("kit.json", "SHA256SUMS"))
      .sorted
      .map(rel => PluginKit.KitFile(rel, sha256(kit.resolve(rel))))
    val main =
      Try(Files.readString(kit.resolve("main-class")).trim).getOrElse("com.histmersion.server.histmersionServer")
    PluginKit.KitManifest(PluginKit.KitFormat, version, commit, graalvm, Spi.Version, main, files)
