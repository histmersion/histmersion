package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.GoalPlans.*

object AgentStateCmd:

  private val list = Opts.subcommand("list", "List all agent state keys") {
    Opts.unit.map(_ => (c: Client) => c.agentStateList().map(entries => println(Markdown.agentStateList(entries))))
  }

  private val get = Opts.subcommand("get", "Get one agent state entry") {
    Opts.argument[String]("key").map { key => (c: Client) =>
      c.agentStateGet(key).map(e => println(Markdown.agentState(e)))
    }
  }

  private val set = Opts.subcommand("set", "Set an agent state key (value as JSON)") {
    (Opts.argument[String]("key"), Opts.argument[String]("value"))
      .mapN { (key, valueStr) => (c: Client) =>
        io.circe.parser.parse(valueStr) match
          case Right(_) =>
            c.agentStateSet(key, AgentStatePut(valueStr)).map(e => println(Markdown.agentState(e)))
          case Left(err) =>
            Left(CliError(s"Invalid JSON: ${err.getMessage}", exitCode = ExitCode.Usage))
      }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("agent-state", "Agent state key-value store: list, get, set") {
      list orElse get orElse set
    }
