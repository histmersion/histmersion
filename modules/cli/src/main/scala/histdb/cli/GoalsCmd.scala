package histdb.cli

import cats.syntax.all.*
import com.monovore.decline.*
import histdb.core.*

object GoalsCmd:

  private val list = Opts.subcommand("list", "List research goals") {
    (Opts.option[String]("status", s"Filter: ${GoalStatus.all.mkString("|")}").orNone,
      Opts.option[Int]("limit", "Max results").orNone)
      .mapN { (status, limit) => (c: Client) =>
        c.listGoals(Api.GoalQuery(status, limit, None))
          .map(r => println(Markdown.goalList(r)))
      }
  }

  private val get = Opts.subcommand("get", "Show one goal by slug") {
    Opts.argument[String]("slug").map { slug => (c: Client) =>
      c.getGoal(slug).map(g => println(Markdown.goal(g)))
    }
  }

  private val update = Opts.subcommand("update", "Update goal fields (patch)") {
    (Opts.argument[String]("slug"),
      Opts.option[String]("title", "New title").orNone,
      Opts.option[String]("status", s"New status: ${GoalStatus.all.mkString("|")}").orNone,
      Opts.option[String]("notes", "Progress notes").orNone)
      .mapN { (slug, title, status, notes) => (c: Client) =>
        c.patchGoal(slug, GoalPatch(title = title, status = status, notes = notes))
          .map(g => println(Markdown.goal(g)))
      }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("goals", "Research goals: list, get, update") {
      list orElse get orElse update
    }

object AgentStateCmd:

  private val list = Opts.subcommand("list", "List all agent state keys") {
    Opts.unit.map(_ => (c: Client) =>
      c.agentStateList().map(entries => println(Markdown.agentStateList(entries))))
  }

  private val get = Opts.subcommand("get", "Get one agent state entry") {
    Opts.argument[String]("key").map { key => (c: Client) =>
      c.agentStateGet(key).map(e => println(Markdown.agentState(e)))
    }
  }

  private val set = Opts.subcommand("set", "Set an agent state key (value as JSON)") {
    (Opts.argument[String]("key"),
      Opts.argument[String]("value"))
      .mapN { (key, valueStr) => (c: Client) =>
        io.circe.parser.parse(valueStr) match
          case Right(_) =>
            c.agentStateSet(key, AgentStatePut(valueStr)).map(e => println(Markdown.agentState(e)))
          case Left(err) =>
            Left(CliError(s"Invalid JSON: ${err.getMessage}", exitCode = 1))
      }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("agent-state", "Agent state key-value store: list, get, set") {
      list orElse get orElse set
    }
