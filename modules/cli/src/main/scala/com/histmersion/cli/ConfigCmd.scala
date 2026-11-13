package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.config.*

import java.nio.file.Files
import com.histmersion.api.settings.ReloadMarkdown
import com.histmersion.util.console.Console

/** `histmersion config …` — the user config file. */
object ConfigCmd:

  /** `histmersion config reload` — the SERVER reads its config file again and applies it in place
    * (docs/research-plugins.md §3.5); exit 2 when an error left its config unchanged.
    */

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Path()
    case Reload(json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Path() =>
          val _ = c
          val located = UserPaths.configFile(Environment.of(org.ekrich.config.ConfigFactory.systemEnvironment()).lookup)
          located match
            case None =>
              Left(
                Cli.Error(
                  Problem.Unclassified(
                    Legacy.Text(s"no config file location: HOME is unset (set ${UserPaths.ConfigEnv})")
                  )
                )
              )
            case Some(p) =>
              Cli.out.println(p)
              AppConfig.loadWithUserFile(org.ekrich.config.ConfigFactory.systemEnvironment(), Some(p)) match
                case Left(err) =>
                  val all = err.toVector
                  all.init.foreach(p => Cli.err.println(s"error: ${p.sentence}"))
                  Left(Cli.Error(all.last))
                case Right(l) =>
                  if !Files.exists(p) then Cli.err.println("(not there yet: the defaults and the environment apply)")
                  else
                    l.file.warnings.foreach(w => Cli.err.println(s"warning: ${w.sentence}"))
                    if l.file.unknownKeys.isEmpty then Cli.err.println("(reads cleanly)")
                  Right(())
        case Command.Reload(json) =>
          c.configReload.flatMap { r =>
            Output.emit(json, r)(ReloadMarkdown.report)
            if r.errors.isEmpty then Right(())
            else
              Left(
                Cli.Error(Problem.Unclassified(Legacy.Text("the config was not reloaded")), exitCode = ExitCode.Refused)
              )
          }
