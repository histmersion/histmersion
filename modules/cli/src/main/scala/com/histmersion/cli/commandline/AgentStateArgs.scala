package com.histmersion
package cli
package commandline

import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.automation.AgentStateMarkdown
import com.histmersion.config.*
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.api.automation.AgentStatePut
import com.histmersion.util.console.Console
import com.histmersion.api.Api
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[AgentStateCmd]]. */
object AgentStateArgs:
  object AgentStateEntry extends CommandLine.Entry:
    val name = "agent-state"
    def help: String = "Agent state key-value store: list, get, set"
    def conf(): Subcommand = GroupAgentState()

  final private class LeafListCmd()
      extends Leaf("histmersion agent-state", "list", "List all agent state keys")
      with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(AgentStateCmd.Command.ListCmd())

  final private class LeafGet()
      extends Leaf("histmersion agent-state", "get", "Get one agent state entry")
      with SharedFlags:
    private val key_ = arg[String]("key")

    def command: Either[Problem, Cli.Command] = Right(AgentStateCmd.Command.Get(key_()))

  final private class LeafSetCmd()
      extends Leaf("histmersion agent-state", "set", "Set an agent state key (value as JSON)")
      with SharedFlags:
    private val key_ = arg[String]("key")
    private val valueStr_ = arg[String]("value")

    def command: Either[Problem, Cli.Command] = Right(AgentStateCmd.Command.SetCmd(key_(), valueStr_()))

  final private class GroupAgentState()
      extends Group("histmersion", "agent-state", "Agent state key-value store: list, get, set"):
    holds(LeafListCmd(), LeafGet(), LeafSetCmd())
