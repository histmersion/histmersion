package com.histmersion
package cli
package commandline

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence
import hearth.kindlings.optics.syntax.*
import com.histmersion.config.*
import com.histmersion.config.edit.*
import org.ekrich.config.ConfigFactory
import com.histmersion.api.settings.{ConfigEdit, ConfigEditMarkdown}
import com.histmersion.util.console.Console
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[SettingsCmd]]. */
object SettingsArgs:
  /** `config get|set|unset|restart`, held by `config`. */
  def leaves(): List[Subcommand] = List(LeafGet(), LeafSetCmd(), LeafUnset(), LeafRestart())

  final private class LeafGet() extends Leaf("histmersion config", "get", "A setting (or every setting, or those under --prefix): what the settings file says, what applies and from where, " +
      "its default and what it does — a credential only as set or not") with SharedFlags:
    private val key_ = arg[String]("key", required = false)
    private val prefix_ = value[String]("prefix", "The settings under this block, e.g. acquire")
    private val onlyLocal_ = localFlag()
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(SettingsCmd.Command.Get(key_.toOption, prefix_.toOption, onlyLocal_(), json_()))

  final private class LeafSetCmd() extends Leaf("histmersion config", "set", "Change a setting in the settings file, in place (comments, order, includes and anything written by hand are kept, " +
      "the old file is kept as .bak), checked first; then the server applies it") with SharedFlags:
    private val key_ = arg[String]("key")
    private val value_ = arg[String]("value")
    private val onlyLocal_ = localFlag()
    private val nr_ = noReloadFlag()
    private val consent_ = literalSecretFlag()
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(SettingsCmd.Command.SetCmd(key_(), value_(), onlyLocal_(), nr_(), consent_(), json_()))

  final private class LeafUnset() extends Leaf("histmersion config", "unset", "Take a setting out of the settings file (back to its default), in place; then the server applies it") with SharedFlags:
    private val key_ = arg[String]("key")
    private val onlyLocal_ = localFlag()
    private val nr_ = noReloadFlag()
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(SettingsCmd.Command.Unset(key_(), onlyLocal_(), nr_(), json_()))

  final private class LeafRestart() extends Leaf("histmersion config", "restart", "Restart the server, for the settings only a restart applies (where it runs under its service: LaunchAgent, systemd)") with SharedFlags:
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(SettingsCmd.Command.Restart(json_()))
