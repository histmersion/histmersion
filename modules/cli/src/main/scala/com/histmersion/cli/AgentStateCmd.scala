package com.histmersion.cli

import com.histmersion.api.automation.AgentStateMarkdown

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.api.automation.AgentStatePut

object AgentStateCmd:

  private val list = Opts.subcommand("list", "List all agent state keys") {
    Opts.unit.map(_ =>
      (c: Client) => c.agentStateList().map(entries => com.histmersion.cli.Cli.writeLine(AgentStateMarkdown.agentStateList(entries)))
    )
  }

  private val get = Opts.subcommand("get", "Get one agent state entry") {
    Opts.argument[String]("key").map { key => (c: Client) =>
      c.agentStateGet(key).map(e => com.histmersion.cli.Cli.writeLine(AgentStateMarkdown.agentState(e)))
    }
  }

  private val set = Opts.subcommand("set", "Set an agent state key (value as JSON)") {
    (Opts.argument[String]("key"), Opts.argument[String]("value"))
      .mapN { (key, valueStr) => (c: Client) =>
        io.circe.parser.parse(valueStr) match
          case Right(_) =>
            c.agentStateSet(key, AgentStatePut(valueStr)).map(e => com.histmersion.cli.Cli.writeLine(AgentStateMarkdown.agentState(e)))
          case Left(err) =>
            Left(Cli.Error(s"Invalid JSON: ${err.getMessage}", exitCode = ExitCode.Usage))
      }
  }

  val command: Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("agent-state", "Agent state key-value store: list, get, set") {
      list orElse get orElse set
    }
