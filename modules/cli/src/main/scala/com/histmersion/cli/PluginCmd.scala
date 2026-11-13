package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}

import cats.syntax.all.*
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.util.plugins.PluginKit
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.util.console.Console

object PluginCmd:

  /** The install on this machine, wherever the CLI is pointed. */

  private[cli] def install(using console: Console): Either[Cli.Error, LocalInstall] =
    val env: UserPaths.Lookup = k => Option(java.lang.System.getenv(k.value)).map(Environment.VariablesValue(_))
    val exe =
      scala.util.Try(ProcessHandle.current().info().command().orElse(null)).toOption.flatMap(Option(_)).map(Path.of(_))
    val cfg = Settings.app
    LocalInstall
      .locate(
        cfg.cli.plugins,
        cfg.runtime.dataDir,
        env,
        UserPaths.OsName(java.lang.System.getProperty("os.name", "")),
        exe
      )
      .left
      .map(e => Cli.Error(e, exitCode = ExitCode.Usage))

  private[cli] def local(c: Client)(using console: Console): Either[Cli.Error, PluginOps] =
    if !PluginKit.isLoopback(c.serverUrl.value) then
      Left(
        Cli.Error(
          Problem.Unclassified(
            Legacy.Text(
              s"histmersion plugin manages the install on THIS machine, but the CLI is pointed at ${c.serverUrl} — " +
                "run the command on the machine the server runs on"
            )
          ),
          exitCode = ExitCode.Usage
        )
      )
    else install.map(i => PluginOps(i, Settings.app.cli.plugins))

  /** The running server's plugins, when it answers. */
  private def running(c: Client): (Option[PluginsReport], Boolean) =
    c.about.fold(_ => (None, false), a => (Some(a.plugins), true))

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Add(
        arg: String,
        yes: Boolean,
        rebuild: Boolean,
        verify: Boolean,
        unsigned: Boolean,
        sha: Option[String],
        repoArgs: List[String]
    )
    case Rm(what: String, rebuild: Boolean)
    case Listing()
    case Status()
    case Rebuild(noRestart: Boolean, unsigned: Boolean, ignoreMemory: Boolean, jvm: Boolean, check: Boolean)
    case Rollback(noRestart: Boolean)
    case New(
        rawId: String,
        rawKind: String,
        dir: Option[String],
        rawPackage: Option[String],
        repoArg: Option[String],
        agentNotes: Boolean
    )
    case Repo(asUrl: Boolean, m2: Boolean)
    case ApiJar(sources: Boolean)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = PluginCmd.run(c, this)

  /** Carry out one command against the server. */
  def run(c: Client, cmd: Command)(using console: Console): Either[Cli.Error, Unit] =
    cmd match
      case Command.Add(arg, yes, rebuild, verify, unsigned, sha, repoArgs) =>
        val repos = repoArgs
          .traverse(r => PluginRepository.parse(r, Files.isDirectory(_)).map(_.value))
          .left
          .map(e => Cli.Error(Problem.Unclassified(Legacy.Text(e)), exitCode = ExitCode.Usage))
        repos.flatMap(rs => local(c).map(_ -> rs)).flatMap { (ops, rs) =>
          ops
            .add(
              arg,
              AddOptions(
                AddOptions.Yes(yes),
                AddOptions.Unsigned(unsigned),
                AddOptions.Verify(verify),
                sha.map(AddOptions.Sha256(_)),
                rs.map(AddOptions.Repos(_))
              )
            )
            .flatMap { _ =>
              if rebuild then ops.rebuild(Nil)
              else
                Right(
                  Cli.out
                    .println("the running binary does not have it yet: histmersion plugin rebuild (or plugin status)")
                )
            }
        }
      case Command.Rm(what, rebuild) =>
        local(c).flatMap(ops =>
          ops
            .rm(what)
            .flatMap(_ =>
              if rebuild then ops.rebuild(Nil)
              else Right(Cli.out.println("the running binary still has it until: histmersion plugin rebuild"))
            )
        )
      case Command.Listing() =>
        local(c).map(ops => Cli.out.println(ops.list(running(c)._1).mkString("\n")))
      case Command.Status() =>
        local(c).map { ops =>
          val (r, answered) = running(c)
          Cli.out.println(ops.status(r, answered)._2.mkString("\n"))
        }
      case Command.Rebuild(noRestart, unsigned, ignoreMemory, jvm, check) =>
        val args = List(
          noRestart -> "--no-restart",
          unsigned -> "--unsigned",
          ignoreMemory -> "--ignore-memory",
          jvm -> "--jvm",
          check -> "--check"
        ).collect { case (true, a) => a }
        local(c).flatMap(_.rebuild(args))
      case Command.Rollback(noRestart) =>
        local(c).flatMap(_.rebuild("--rollback" :: (if noRestart then List("--no-restart") else Nil)))
      case Command.New(rawId, rawKind, dir, rawPackage, repoArg, agentNotes) =>
        val _ = c
        val result = for
          id <- PluginId.parse(rawId)
          kind <- PluginKind.parse(rawKind)
          pkg <- ScalaPackage.parse(rawPackage.getOrElse(id.packageName.value))
        yield (id, kind, pkg)
        result.left.map(e => Cli.Error(Problem.Unclassified(Legacy.Text(e)), exitCode = ExitCode.Usage)).flatMap {
          (id, kind, pkg) =>
            val target = Path.of(dir.getOrElse(id.value)).toAbsolutePath.normalize
            for
              (repo, copied) <- PluginAuthoring.repository(
                repoArg,
                install,
                PluginAuthoring
                  .mavenLocal(k => Option(java.lang.System.getenv(k.value)).map(LocalInstall.EnvSetting(_)))
              )
              written <- PluginAuthoring.write(
                target,
                PluginTemplate
                  .files(PluginTemplate.Request(id, kind, pkg, repo, PluginTemplate.Request.AgentNotes(agentNotes)))
              )
            yield
              if copied.nonEmpty then
                Cli.out.println(
                  s"copied the install's plugin API, platform and testkit into the local Maven repository (${copied.size} file(s))"
                )
              PluginAuthoring.nextSteps(target, id, pkg, written).foreach(value => Cli.out.println(value))
        }
      case Command.Repo(asUrl, m2) =>
        val _ = c
        if m2 then
          for
            dir <- PluginAuthoring.mavenLocal(k =>
              Option(java.lang.System.getenv(k.value)).map(LocalInstall.EnvSetting(_))
            )
            copied <- PluginAuthoring.publishToMavenLocal(install, dir)
          yield
            Cli.out.println(if asUrl then dir.toUri.toString else dir.toString)
            Cli.err.println(if copied.isEmpty then
              s"Histmersion ${PluginBuildVersions.histmersion}'s plugin artifacts are up to date there"
            else
              s"copied ${copied.size} file(s) of Histmersion ${PluginBuildVersions.histmersion}'s plugin artifacts there")
        else
          PluginAuthoring
            .localRepository(install)
            .map(i => Cli.out.println(if asUrl then i.mavenRepo.toUri.toString else i.mavenRepo.toString))
      case Command.ApiJar(sources) =>
        val _ = c
        PluginAuthoring.apiJars(install).flatMap { (jar, src) =>
          if !sources then Right(Cli.out.println(jar))
          else
            Either.cond(
              Files.isRegularFile(src),
              Cli.out.println(src),
              Cli.Error(Problem.Unclassified(Legacy.Text(s"no sources jar beside $jar")), exitCode = ExitCode.Refused)
            )
        }
