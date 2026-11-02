package com.histmersion.cli

import com.monovore.decline.*
import com.histmersion.config.*

import java.nio.file.Files
import com.histmersion.api.settings.ReloadMarkdown

/** `histmersion config …` — the user config file. */
object ConfigCmd:

  private val path = Opts.subcommand(
    "path",
    "Where the user config file is on this machine (printed alone on stdout), whether it exists and reads cleanly; the running server's file is in histmersion about"
  ) {
    Opts.unit.map(_ =>
      (_: Client) =>
        val located = UserPaths.configFile(AppConfig.lookup(org.ekrich.config.ConfigFactory.systemEnvironment()))
        located match
          case None    => Left(Cli.Error(s"no config file location: HOME is unset (set ${UserPaths.ConfigEnv})"))
          case Some(p) =>
            println(p)
            AppConfig.loadWithUserFile(org.ekrich.config.ConfigFactory.systemEnvironment(), Some(p)) match
              case Left(err) => Left(Cli.Error(err))
              case Right(l)  =>
                if !Files.exists(p) then System.err.println("(not there yet: the defaults and the environment apply)")
                else
                  l.file.warnings.foreach(w => System.err.println(s"warning: $w"))
                  if l.file.unknownKeys.isEmpty then System.err.println("(reads cleanly)")
                Right(())
    )
  }

  /** `histmersion config reload` — the SERVER reads its config file again and applies it in place
    * (docs/research-plugins.md §3.5); exit 2 when an error left its config unchanged.
    */
  private val reload = Opts.subcommand(
    "reload",
    "Make the server read its config file again and apply it in place, no restart (restart-only settings are listed, not applied)"
  ) {
    Output.jsonFlag.map { json => (c: Client) =>
      c.configReload.flatMap { r =>
        Output.emit(json, r)(ReloadMarkdown.report)
        if r.errors.isEmpty then Right(())
        else Left(Cli.Error("the config was not reloaded", exitCode = ExitCode.Refused))
      }
    }
  }

  val command: Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand(
      "config",
      "The settings file: where it is, its settings (get, set, unset — the file is changed in place, comments and all), reloading and restarting the server"
    ) {
      path orElse reload orElse SettingsCmd.get orElse SettingsCmd.set orElse SettingsCmd.unset orElse SettingsCmd.restart
    }
