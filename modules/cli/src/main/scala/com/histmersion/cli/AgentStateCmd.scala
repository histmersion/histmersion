package com.histmersion
package cli
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.automation.AgentStateMarkdown

import com.histmersion.config.*
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.api.automation.AgentStatePut
import com.histmersion.util.console.Console
import com.histmersion.api.Api

object AgentStateCmd:

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case ListCmd()
    case Get(key: String)
    case SetCmd(key: String, valueStr: String)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.ListCmd() =>
          c.agentStateList().map(entries => Cli.out.println(AgentStateMarkdown.agentStateList(entries)))
        case Command.Get(key) =>
          c.agentStateGet(Api.AgentState.Key(key)).map(e => Cli.out.println(AgentStateMarkdown.agentState(e)))
        case Command.SetCmd(key, valueStr) =>
          RawJson.parse(valueStr) match
            case Right(_) =>
              c.agentStateSet(Api.AgentState.Key(key), AgentStatePut(AgentStatePut.Value(valueStr)))
                .map(e => Cli.out.println(AgentStateMarkdown.agentState(e)))
            case Left(err) =>
              Left(
                Cli.Error(
                  Problem.Unclassified(Legacy.Text(s"Invalid JSON: $err"))
                )
              )
