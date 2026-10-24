package com.histmersion.pluginresolver

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.control.NonFatal

/** What one `resolve` run is asked.
  *
  * @param kit             the relink kit: its `modules` and `excludes` are what the server provides
  * @param lib             the plugins directory's `lib/`, written unless `check`
  * @param histmersionRepo the install's Maven repository (the plugin API and platform), looked up first
  * @param repositories    the repositories named for this run, looked up before the ones `lib/repositories`
  *                        keeps from earlier runs (and kept with them); none named and none kept = Maven
  *                        Central
  * @param check           resolve and report only: `lib/` is left as it is
  * @param cache           coursier's cache, when not its default */
final case class ResolveRequest(kit: Path, lib: Path, histmersionRepo: Option[Path], repositories: List[RepositoryUrl],
    check: Boolean, jars: List[Path], cache: Option[Path] = None)

/** The plugins directory's `lib/`: the dependency jars of the installed plugins, and three files.
  * `classpath` lists the jars in class-path order, `inputs` the plugin jars and POMs they were resolved
  * from (`<sha256>  <file>`), and `repositories` the repositories they were resolved from. */
object LibDirectory:
  val Classpath    = "classpath"
  val Inputs       = "inputs"
  val Repositories = "repositories"

  def storedRepositories(lib: Path): Either[String, List[RepositoryUrl]] =
    val f = lib.resolve(Repositories)
    if !Files.isRegularFile(f) then Right(Nil)
    else traverse(Files.readAllLines(f, UTF_8).asScala.toList.map(_.trim).filter(_.nonEmpty))(
      r => RepositoryUrl.either(r).left.map(e => s"$f: '$r': $e"))

  /** Replace what `lib/` holds with `deps`: a jar already there with the same content is kept, every
    * other jar removed. */
  def write(lib: Path, deps: List[Dependency], inputs: List[(String, String)], repositories: List[RepositoryUrl]): Unit =
    Files.createDirectories(lib)
    val wanted = deps.map(_.libName).toSet
    jarsIn(lib).filterNot(p => wanted(p.getFileName.toString)).foreach(Files.delete)
    deps.foreach { d =>
      val target = lib.resolve(d.libName)
      if !Files.isRegularFile(target) || sha256(target) != sha256(d.file) then
        val partial = lib.resolve(d.libName + ".part")
        Files.copy(d.file, partial, StandardCopyOption.REPLACE_EXISTING)
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    Files.writeString(lib.resolve(Classpath), Resolution.classpathFile(deps), UTF_8)
    Files.writeString(lib.resolve(Inputs), Resolution.inputsFile(inputs), UTF_8)
    if repositories.nonEmpty then Files.writeString(lib.resolve(Repositories), repositories.map(_.value + "\n").mkString, UTF_8)

  private def jarsIn(dir: Path): List[Path] =
    val s = Files.list(dir)
    try s.iterator().asScala.filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".jar")).toList
    finally s.close()

  def sha256(p: Path): String =
    val md = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(p)
    try
      val buf = new Array[Byte](1 << 16)
      Iterator.continually(in.read(buf)).takeWhile(_ >= 0).foreach(n => md.update(buf, 0, n))
    finally in.close()
    md.digest().map(b => f"$b%02x").mkString

/** The dependencies of user plugins, resolved together with what the server ships — run from the
  * relink kit by `histmersion-relink --resolve` (which `histmersion plugin add` and `rm`, the relink's
  * pre-flight and the JVM launcher call):
  *
  * {{{
  *   resolve --kit <dir> --lib <dir> [--histmersion-repo <dir>] [--repo <url|dir>]… [--check] <plugin jar>…
  * }}}
  *
  * A plugin's dependencies come from its POM (beside the jar, `x-1.0.pom`, or inside it). Each plugin is
  * resolved on its own first: a module the server ships at an older version than the plugin needs
  * refuses it (one class loader, one version: the server's would win). Then all plugins together,
  * without the server's modules: the server's copy of each, and of everything it depends on, is on
  * the class path already. What remains goes into `lib/`. Exit codes: 0 done, 1 usage, 2 refused,
  * 3 failed (a repository, a download, a file). */
object PluginResolver:

  private val Usage =
    "usage: PluginResolver resolve --kit <dir> --lib <dir> [--histmersion-repo <dir>] [--repo <url|dir>]… [--check] <plugin jar>…"

  def main(args: Array[String]): Unit =
    val rc =
      try run(args.toList, s => System.out.println(s), s => System.err.println(s))
      catch
        case NonFatal(e) =>
          System.err.println(s"PluginResolver: ${e.getClass.getSimpleName}: ${e.getMessage}")
          3
    sys.exit(rc)

  def run(args: List[String], out: String => Unit, err: String => Unit): Int = args match
    case "resolve" :: rest =>
      parse(rest) match
        case Left(e)  => err(s"$e\n$Usage"); 1
        case Right(r) => resolve(r, out, err)
    case _ => err(Usage); 1

  /** Pure: the request the arguments make. */
  def parse(args: List[String]): Either[String, ResolveRequest] =
    @annotation.tailrec
    def loop(rest: List[String], acc: ResolveRequest): Either[String, ResolveRequest] = rest match
      case "--kit" :: d :: t              => loop(t, acc.copy(kit = Path.of(d)))
      case "--lib" :: d :: t              => loop(t, acc.copy(lib = Path.of(d)))
      case "--histmersion-repo" :: d :: t => loop(t, acc.copy(histmersionRepo = Some(Path.of(d))))
      case "--repo" :: r :: t             =>
        RepositoryUrl.fromArgument(r) match
          case Right(url) => loop(t, acc.copy(repositories = acc.repositories :+ url))
          case Left(e)    => Left(s"--repo '$r': $e")
      case "--check" :: t                 => loop(t, acc.copy(check = true))
      case flag :: _ if flag.startsWith("--") => Left(s"unknown option $flag")
      case jars                           => Right(acc.copy(jars = jars.map(Path.of(_))))
    val empty = Path.of("")
    loop(args, ResolveRequest(empty, empty, None, Nil, false, Nil)).filterOrElse(r => r.kit != empty && r.lib != empty,
      "--kit and --lib are required")

  def resolve(r: ResolveRequest, out: String => Unit, err: String => Unit): Int =
    val outcome = for
      server   <- serverModules(r.kit)
      stored   <- LibDirectory.storedRepositories(r.lib)
      missing   = r.jars.filterNot(Files.isRegularFile(_))
      _        <- Either.cond(missing.isEmpty, (), s"no such plugin jar: ${missing.mkString(", ")}")
    yield (server, stored)
    outcome match
      case Left(e) => err(e); 3
      case Right((server, stored)) =>
        val named   = (r.repositories ++ stored).distinct
        val remote  = if named.nonEmpty then named else List(Coursier.central)
        val plugins = r.jars.map(j => PluginArtifact(j, Pom.of(j)))
        val scratch = Files.createTempDirectory("histmersion-plugin-resolve")
        try
          Coursier.pluginRepository(scratch, plugins) match
            case Left(e) => err(s"refused: $e"); 2
            case Right(roots) =>
              val local    = (Some(scratch) ++ r.histmersionRepo).map(d => RepositoryUrl.applyUnsafe(d.toUri.toString)).toList
              val coursier = Coursier(local ++ remote, r.cache)
              resolveRoots(roots, server, coursier, out, err) match
                case Left(rc) => rc
                case Right(deps) =>
                  if !r.check then LibDirectory.write(r.lib, deps, inputs(r.jars), (r.repositories ++ stored).distinct)
                  0
        finally deleteTree(scratch)

  private def resolveRoots(roots: List[(PluginArtifact, ModuleCoordinates)], server: ServerModules,
      coursier: Coursier, out: String => Unit, err: String => Unit): Either[Int, List[Dependency]] =
    if roots.isEmpty then Right(Nil) else resolveSome(roots, server, coursier, out, err)

  private def resolveSome(roots: List[(PluginArtifact, ModuleCoordinates)], server: ServerModules,
      coursier: Coursier, out: String => Unit, err: String => Unit): Either[Int, List[Dependency]] =
    val pluginKeys = roots.map(_._2.key).toSet
    val alone = traverse(roots) { (plugin, c) =>
      coursier.resolve(List(c), server.excluded).left.map(e => s"${plugin.fileName}: $e").map(m => plugin.fileName -> m.map(_._1))
    }
    alone match
      case Left(e) => err(s"could not resolve the plugins' dependencies: $e"); Left(3)
      case Right(perPlugin) =>
        perPlugin.flatMap((p, modules) => Resolution.conflicts(p, modules, server, Coursier.order)) match
          case conflicts if conflicts.nonEmpty =>
            conflicts.foreach(c => err(s"refused: ${c.message}"))
            Left(2)
          case _ =>
            coursier.resolve(roots.map(_._2), server.shipped.keySet ++ server.excluded) match
              case Left(e) => err(s"could not resolve the plugins' dependencies: $e"); Left(3)
              case Right(joint) =>
                val deps     = Resolution.dependencies(joint, pluginKeys, server, Resolution.neededBy(perPlugin))
                val provided = Resolution.provided(perPlugin.flatMap(_._2), pluginKeys, server)
                Resolution.report(deps, provided).foreach(out)
                Right(deps)

  private def serverModules(kit: Path): Either[String, ServerModules] =
    val modules  = kit.resolve("modules")
    val excludes = kit.resolve("excludes")
    if !Files.isRegularFile(modules) then Left(s"no module list in the kit ($modules): this kit predates plugin dependencies")
    else
      def lines(p: Path) = if Files.isRegularFile(p) then Files.readAllLines(p, UTF_8).asScala.toList else Nil
      ServerModules.parse(lines(modules), lines(excludes)).left.map(e => s"$modules: $e")

  /** Every plugin jar and the POM beside it, as `lib/inputs` lists them. */
  private def inputs(jars: List[Path]): List[(String, String)] =
    jars.flatMap(j => j :: List(Pom.besideJar(j)).filter(Files.isRegularFile(_)))
      .map(p => p.getFileName.toString -> LibDirectory.sha256(p))

  private def deleteTree(dir: Path): Unit =
    Try {
      val s = Files.walk(dir)
      try s.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists)
      finally s.close()
    }
    ()
