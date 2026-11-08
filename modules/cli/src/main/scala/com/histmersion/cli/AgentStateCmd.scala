package com.histmersion
package cli

import com.histmersion.api.automation.AgentStateMarkdown

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.api.automation.AgentStatePut
import com.histmersion.util.console.Console
import com.histmersion.api.Api

object AgentStateCmd:

  private def list(using console: Console) = Opts.subcommand("list", "List all agent state keys") {
    Opts.unit.map(_ =>
      (c: Client) => c.agentStateList().map(entries => Cli.out.println(AgentStateMarkdown.agentStateList(entries)))
    )
  }

  private def get(using console: Console) = Opts.subcommand("get", "Get one agent state entry") {
    Opts.argument[String]("key").map { key => (c: Client) =>
      c.agentStateGet(Api.AgentState.Key(key)).map(e => Cli.out.println(AgentStateMarkdown.agentState(e)))
    }
  }

  private def set(using console: Console) = Opts.subcommand("set", "Set an agent state key (value as JSON)") {
    (Opts.argument[String]("key"), Opts.argument[String]("value"))
      .mapN { (key, valueStr) => (c: Client) =>
        io.circe.parser.parse(valueStr) match
          case Right(_) =>
            c.agentStateSet(Api.AgentState.Key(key), AgentStatePut(AgentStatePut.Value(valueStr)))
              .map(e => Cli.out.println(AgentStateMarkdown.agentState(e)))
          case Left(err) =>
            Left(Cli.Error(Cli.Error.Message(s"Invalid JSON: ${err.getMessage}"), exitCode = ExitCode.Usage))
      }
  }

  def command(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("agent-state", "Agent state key-value store: list, get, set") {
      list orElse get orElse set
    }
