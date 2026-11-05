package com.histmersion.pluginresolver

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.control.NonFatal
import com.histmersion.util.console.Console

/** The dependencies of user plugins, resolved together with what the server ships — run from the relink kit by
  * `histmersion-relink --resolve` (which `histmersion plugin add` and `rm`, the relink's pre-flight and the JVM
  * launcher call):
  *
  * {{{
  *   resolve --kit <dir> --lib <dir> [--histmersion-repo <dir>] [--repo <url|dir>]… [--check] <plugin jar>…
  * }}}
  *
  * A plugin's dependencies come from its POM (beside the jar, `x-1.0.pom`, or inside it). Each plugin is resolved on
  * its own first: a module the server ships at an older version than the plugin needs refuses it (one class loader, one
  * version: the server's would win). Then all plugins together, without the server's modules: the server's copy of
  * each, and of everything it depends on, is on the class path already. What remains goes into `lib/`. Exit codes: 0
  * done, 1 usage, 2 refused, 3 failed (a repository, a download, a file).
  */
@main def pluginResolver(args: String*): Unit = sys.exit(DependencyResolver.exitCode(args.toList)(using Console.system))

object DependencyResolver:

  type ExitCodeResult = ExitCodeResult.T
  object ExitCodeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExitCodeArgs = ExitCodeArgs.T
  object ExitCodeArgs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResult = RunResult.T
  object RunResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunArgs = RunArgs.T
  object RunArgs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseArgs = ParseArgs.T
  object ParseArgs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResult = ResolveResult.T
  object ResolveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveRootsResultPart1 = ResolveRootsResultPart1.T
  object ResolveRootsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveSomeResultPart1 = ResolveSomeResultPart1.T
  object ResolveSomeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ServerModulesResultPart1 = ServerModulesResultPart1.T
  object ServerModulesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InputsResultPart1 = InputsResultPart1.T
  object InputsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InputsResultPart2 = InputsResultPart2.T
  object InputsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Usage =
    "usage: pluginResolver resolve --kit <dir> --lib <dir> [--histmersion-repo <dir>] [--repo <url|dir>]… [--check] <plugin jar>…"

  def exitCode(args: List[com.histmersion.pluginresolver.DependencyResolver.ExitCodeArgs])(using console: Console): com.histmersion.pluginresolver.DependencyResolver.ExitCodeResult = try run(args)(using console)
    catch
      case NonFatal(e) =>
        console.errorLine(s"pluginResolver: ${e.getClass.getSimpleName}: ${e.getMessage}")
        3

  def run(args: List[com.histmersion.pluginresolver.DependencyResolver.RunArgs])(using console: Console): com.histmersion.pluginresolver.DependencyResolver.RunResult = args match
    case "resolve" :: rest =>
      parse(rest) match
        case Left(e)  => console.errorLine(s"$e\n$Usage"); 1
        case Right(r) => resolve(r)(using console)
    case _ => console.errorLine(Usage); 1

  /** Pure: the request the arguments make. */
  def parse(args: List[com.histmersion.pluginresolver.DependencyResolver.ParseArgs]): Either[com.histmersion.pluginresolver.DependencyResolver.ParseResultPart1, ResolveRequest] =
    @annotation.tailrec
    def loop(rest: List[String], acc: ResolveRequest): Either[String, ResolveRequest] = rest match
      case "--kit" :: d :: t              => loop(t, acc.copy(kit = Path.of(d)))
      case "--lib" :: d :: t              => loop(t, acc.copy(lib = Path.of(d)))
      case "--histmersion-repo" :: d :: t => loop(t, acc.copy(histmersionRepo = Some(Path.of(d))))
      case "--repo" :: r :: t             =>
        RepositoryUrl.fromArgument(r) match
          case Right(url) => loop(t, acc.copy(repositories = acc.repositories :+ url))
          case Left(e)    => Left(s"--repo '$r': $e")
      case "--check" :: t                     => loop(t, acc.copy(check = true))
      case flag :: _ if flag.startsWith("--") => Left(s"unknown option $flag")
      case jars                               => Right(acc.copy(jars = jars.map(Path.of(_))))
    val empty = Path.of("")
    loop(args, ResolveRequest(empty, empty, None, Nil, false, Nil))
      .filterOrElse(r => r.kit != empty && r.lib != empty, "--kit and --lib are required")

  def resolve(r: ResolveRequest)(using console: Console): com.histmersion.pluginresolver.DependencyResolver.ResolveResult =
    val outcome = for
      server <- serverModules(r.kit)
      stored <- LibDirectory.storedRepositories(r.lib)
      missing = r.jars.filterNot(Files.isRegularFile(_))
      _ <- Either.cond(missing.isEmpty, (), s"no such plugin jar: ${missing.mkString(", ")}")
    yield (server, stored)
    outcome match
      case Left(e)                 => console.errorLine(e); 3
      case Right((server, stored)) =>
        val named = (r.repositories ++ stored).distinct
        val remote = if named.nonEmpty then named else List(Coursier.central)
        val plugins = r.jars.map(j => PluginArtifact(j, Pom.of(j)))
        val scratch = Files.createTempDirectory("histmersion-plugin-resolve")
        try
          Coursier.pluginRepository(scratch, plugins) match
            case Left(e)      => console.errorLine(s"refused: $e"); 2
            case Right(roots) =>
              val local =
                (Some(scratch) ++ r.histmersionRepo).map(d => RepositoryUrl.applyUnsafe(d.toUri.toString)).toList
              val coursier = Coursier(local ++ remote, r.cache)
              resolveRoots(roots, server, coursier)(using console) match
                case Left(rc)    => rc
                case Right(deps) =>
                  if !r.check then LibDirectory.write(r.lib, deps, inputs(r.jars), (r.repositories ++ stored).distinct)
                  0
        finally deleteTree(scratch)

  private def resolveRoots(
      roots: List[(PluginArtifact, ModuleCoordinates)],
      server: ServerModules,
      coursier: Coursier
  )(using console: Console): Either[com.histmersion.pluginresolver.DependencyResolver.ResolveRootsResultPart1, List[Dependency]] =
    if roots.isEmpty then Right(Nil) else resolveSome(roots, server, coursier)(using console)

  private def resolveSome(
      roots: List[(PluginArtifact, ModuleCoordinates)],
      server: ServerModules,
      coursier: Coursier
  )(using console: Console): Either[com.histmersion.pluginresolver.DependencyResolver.ResolveSomeResultPart1, List[Dependency]] =
    val pluginKeys = roots.map(_._2.key).toSet
    val alone = traverse(roots) { (plugin, c) =>
      coursier
        .resolve(List(c), server.excluded)
        .left
        .map(e => s"${plugin.fileName}: $e")
        .map(m => plugin.fileName -> m.map(_._1))
    }
    alone match
      case Left(e)          => console.errorLine(s"could not resolve the plugins' dependencies: $e"); Left(3)
      case Right(perPlugin) =>
        perPlugin.flatMap((p, modules) => Resolution.conflicts(p, modules, server, Coursier.order)) match
          case conflicts if conflicts.nonEmpty =>
            conflicts.foreach(c => console.errorLine(s"refused: ${c.message}"))
            Left(2)
          case _ =>
            coursier.resolve(roots.map(_._2), server.shipped.keySet ++ server.excluded) match
              case Left(e)      => console.errorLine(s"could not resolve the plugins' dependencies: $e"); Left(3)
              case Right(joint) =>
                val deps = Resolution.dependencies(joint, pluginKeys, server, Resolution.neededBy(perPlugin))
                val provided = Resolution.provided(perPlugin.flatMap(_._2), pluginKeys, server)
                Resolution.report(deps, provided).foreach(console.writeLine)
                Right(deps)

  private def serverModules(kit: Path): Either[com.histmersion.pluginresolver.DependencyResolver.ServerModulesResultPart1, ServerModules] =
    val modules = kit.resolve("modules")
    val excludes = kit.resolve("excludes")
    if !Files.isRegularFile(modules) then
      Left(s"no module list in the kit ($modules): this kit predates plugin dependencies")
    else
      def lines(p: Path) = if Files.isRegularFile(p) then Files.readAllLines(p, UTF_8).asScala.toList else Nil
      ServerModules.parse(lines(modules), lines(excludes)).left.map(e => s"$modules: $e")

  /** Every plugin jar and the POM beside it, as `lib/inputs` lists them. */
  private def inputs(jars: List[Path]): List[(com.histmersion.pluginresolver.DependencyResolver.InputsResultPart1, com.histmersion.pluginresolver.DependencyResolver.InputsResultPart2)] =
    jars
      .flatMap(j => j :: List(Pom.besideJar(j)).filter(Files.isRegularFile(_)))
      .map(p => p.getFileName.toString -> LibDirectory.sha256(p))

  private def deleteTree(dir: Path): Unit =
    Try {
      val s = Files.walk(dir)
      try s.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists)
      finally s.close()
    }
    ()
