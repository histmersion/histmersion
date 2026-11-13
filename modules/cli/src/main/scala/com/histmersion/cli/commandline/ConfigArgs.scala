package com.histmersion
package cli
package commandline

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence
import com.histmersion.config.*
import java.nio.file.Files
import com.histmersion.api.settings.ReloadMarkdown
import com.histmersion.util.console.Console
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[ConfigCmd]]. */
object ConfigArgs:
  object ConfigEntry extends CommandLine.Entry:
    val name = "config"
    def help: String =
      "The settings file: where it is, its settings (get, set, unset — the file is changed in place, comments and all), reloading and restarting the server"
    def conf(): Subcommand = GroupConfig()

  final private class LeafPath()
      extends Leaf(
        "histmersion config",
        "path",
        "Where the user config file is on this machine (printed alone on stdout), whether it exists and reads cleanly; the running server's file is in histmersion about"
      )
      with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(ConfigCmd.Command.Path())

  final private class LeafReload()
      extends Leaf(
        "histmersion config",
        "reload",
        "Make the server read its config file again and apply it in place, no restart (restart-only settings are listed, not applied)"
      )
      with SharedFlags:
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(ConfigCmd.Command.Reload(json_()))

  final private class GroupConfig()
      extends Group(
        "histmersion",
        "config",
        "The settings file: where it is, its settings (get, set, unset — the file is changed in place, comments and all), reloading and restarting the server"
      ):
    holds((LeafPath() :: LeafReload() :: ConfigSettingArgs.leaves())*)
