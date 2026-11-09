package com.histmersion
package pluginresolver

import com.histmersion.util.problem.{Legacy, Problem}
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
@main def pluginResolver(args: String*): Unit = sys.exit(
  (DependencyResolver
    .exitCode((args.toList).map(refactorValue3 => Resolver.Argument(refactorValue3)))(using Console.system))
    .value
)

object DependencyResolver:
  private given CanEqual[Path, Path] = CanEqual.derived

  export Resolver.{
    Argument as ExitCodeArgs,
    Argument as ParseArgs,
    Argument as ParseLoopRest,
    Argument as RunArgs,
    ExitCode as ExitCodeResult,
    ExitCode as ResolveResult,
    ExitCode as RunResult
  }

  private val Usage =
    "usage: pluginResolver resolve --kit <dir> --lib <dir> [--histmersion-repo <dir>] [--repo <url|dir>]… [--check] <plugin jar>…"

  def exitCode(args: List[DependencyResolver.ExitCodeArgs])(using console: Console): DependencyResolver.ExitCodeResult =
    try run(args)(using console)
    catch
      case NonFatal(e) =>
        console.errorLine(s"pluginResolver: ${e.getClass.getSimpleName}: ${e.getMessage}")
        DependencyResolver.ExitCodeResult(3)

  def run(args: List[DependencyResolver.RunArgs])(using console: Console): DependencyResolver.RunResult = args match
    case refactorPattern :: rest if (refactorPattern.value == "resolve") =>
      parse(rest) match
        case Left(e)  => console.errorLine(s"${e.legacyText}\n$Usage"); DependencyResolver.RunResult(1)
        case Right(r) => resolve(r)(using console)
    case _ => console.errorLine(Usage); DependencyResolver.RunResult(1)

  /** Pure: the request the arguments make. */
  def parse(args: List[DependencyResolver.ParseArgs]): Either[Problem, ResolveRequest] =
    @annotation.tailrec
    def loop(
        rest: List[DependencyResolver.ParseLoopRest],
        acc: ResolveRequest
    ): Either[Problem, ResolveRequest] = rest match
      case refactorPattern2 :: d :: t if (refactorPattern2.value == "--kit") =>
        loop(t, acc.copy(kit = Path.of((d).value)))
      case refactorPattern3 :: d :: t if (refactorPattern3.value == "--lib") =>
        loop(t, acc.copy(lib = Path.of((d).value)))
      case refactorPattern4 :: d :: t if (refactorPattern4.value == "--histmersion-repo") =>
        loop(t, acc.copy(histmersionRepo = Some(Path.of((d).value))))
      case refactorPattern5 :: r :: t if (refactorPattern5.value == "--repo") =>
        RepositoryUrl.fromArgument(Resolver.Argument((r).value)) match
          case Right(url) => loop(t, acc.copy(repositories = acc.repositories :+ url))
          case Left(e)    => Left(Problem.Unclassified(Legacy.Text(s"--repo '$r': ${e.legacyText}")))
      case refactorPattern6 :: t if (refactorPattern6.value == "--check") =>
        loop(t, acc.copy(check = ResolveRequest.Check(true)))
      case flag :: _ if (flag).value.startsWith("--") =>
        Left(Problem.Unclassified(Legacy.Text(s"unknown option $flag")))
      case jars => Right(acc.copy(jars = jars.map((refactorValue4 => Path.of(refactorValue4.value)))))
    val empty = Path.of("")
    loop(args, ResolveRequest(empty, empty, None, Nil, ResolveRequest.Check(false), Nil))
      .filterOrElse(
        r => r.kit != empty && r.lib != empty,
        Problem.Unclassified(Legacy.Text("--kit and --lib are required"))
      )

  def resolve(r: ResolveRequest)(using console: Console): DependencyResolver.ResolveResult =
    val outcome = for
      server <- serverModules(r.kit)
      stored <- LibDirectory.storedRepositories(r.lib)
      missing = r.jars.filterNot(Files.isRegularFile(_))
      _ <- Either.cond(
        missing.isEmpty,
        (),
        Problem.Unclassified(Legacy.Text(s"no such plugin jar: ${missing.mkString(", ")}"))
      )
    yield (server, stored)
    outcome match
      case Left(e)                 => console.errorLine((e).legacyText); DependencyResolver.ResolveResult(3)
      case Right((server, stored)) =>
        val named = (r.repositories ++ stored).distinct
        val remote = if named.nonEmpty then named else List(Coursier.central)
        val plugins = r.jars.map(j => PluginArtifact(j, Pom.of(j)))
        val scratch = Files.createTempDirectory("histmersion-plugin-resolve")
        try
          Coursier.pluginRepository(scratch, plugins) match
            case Left(e)      => console.errorLine(s"refused: ${e.legacyText}"); DependencyResolver.ResolveResult(2)
            case Right(roots) =>
              val local =
                (Some(scratch) ++ r.histmersionRepo).map(d => RepositoryUrl.applyUnsafe(d.toUri.toString)).toList
              val coursier = Coursier(local ++ remote, r.cache)
              resolveRoots(roots, server, coursier)(using console) match
                case Left(rc)    => rc
                case Right(deps) =>
                  if !(r.check).value then
                    LibDirectory.write(r.lib, deps, inputs(r.jars), (r.repositories ++ stored).distinct)
                  DependencyResolver.ResolveResult(0)
        finally deleteTree(scratch)

  private def resolveRoots(
      roots: List[(PluginArtifact, ModuleCoordinates)],
      server: ServerModules,
      coursier: Coursier
  )(using console: Console): Either[Resolver.ExitCode, List[Dependency]] =
    if roots.isEmpty then Right(Nil) else resolveSome(roots, server, coursier)(using console)

  private def resolveSome(
      roots: List[(PluginArtifact, ModuleCoordinates)],
      server: ServerModules,
      coursier: Coursier
  )(using console: Console): Either[Resolver.ExitCode, List[Dependency]] =
    val pluginKeys = roots.map(_._2.key).toSet
    val alone = traverse(roots) { (plugin, c) =>
      coursier
        .resolve(List(c), server.excluded)
        .left
        .map(e => Problem.Unclassified(Legacy.Text(s"${plugin.fileName}: ${e.legacyText}")))
        .map(m => plugin.fileName -> m.map(_._1))
    }
    alone match
      case Left(e) =>
        console.errorLine(s"could not resolve the plugins' dependencies: ${e.legacyText}");
        Left(Resolver.ExitCode(3))
      case Right(perPlugin) =>
        perPlugin.flatMap((p, modules) => Resolution.conflicts(p, modules, server, Coursier.order)) match
          case conflicts if conflicts.nonEmpty =>
            conflicts.foreach(c => console.errorLine(s"refused: ${c.message.legacyText}"))
            Left(Resolver.ExitCode(2))
          case _ =>
            coursier.resolve(roots.map(_._2), server.shipped.keySet ++ server.excluded) match
              case Left(e) =>
                console.errorLine(s"could not resolve the plugins' dependencies: ${e.legacyText}");
                Left(Resolver.ExitCode(3))
              case Right(joint) =>
                val deps = Resolution.dependencies(joint, pluginKeys, server, Resolution.neededBy(perPlugin))
                val provided = Resolution.provided(perPlugin.flatMap(_._2), pluginKeys, server)
                Resolution.report(deps, provided).foreach(console.writeLine)
                Right(deps)

  private def serverModules(kit: Path): Either[Problem, ServerModules] =
    val modules = kit.resolve("modules")
    val excludes = kit.resolve("excludes")
    if !Files.isRegularFile(modules) then
      Left(
        Problem.Unclassified(
          Legacy.Text(s"no module list in the kit ($modules): this kit predates plugin dependencies")
        )
      )
    else
      def lines(p: Path) = if Files.isRegularFile(p) then Files.readAllLines(p, UTF_8).asScala.toList else Nil
      ServerModules
        .parse(
          (lines(modules)).map(refactorValue => Resolver.ManifestLine(refactorValue)),
          (lines(excludes)).map(refactorValue2 => Resolver.ManifestLine(refactorValue2))
        )
        .left
        .map(e => Problem.Unclassified(Legacy.Text(s"$modules: ${e.legacyText}")))

  /** Every plugin jar and the POM beside it, as `lib/inputs` lists them. */
  private def inputs(
      jars: List[Path]
  ): List[(Resolver.FileName, Resolver.Sha256)] =
    jars
      .flatMap(j => j :: List(Pom.besideJar(j)).filter(Files.isRegularFile(_)))
      .map { p =>
        val refactorTuple = p.getFileName.toString -> LibDirectory.sha256(p);
        (Resolver.FileName(refactorTuple._1), refactorTuple._2)
      }

  private def deleteTree(dir: Path): Unit =
    Try {
      val s = Files.walk(dir)
      try s.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists)
      finally s.close()
    }
    ()
