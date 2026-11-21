package com.histmersion
package cli
package commandline

import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.api.time.TimeBound
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*

object LogArgs:
  private val Help = "What happened, step by step: answered requests, provider calls, problems and database writes, " +
    "each with the trace that links one request's or job's steps"

  object LogEntry extends CommandLine.Entry:
    val name = "log"
    def help: String = Help
    def conf(): Subcommand = LeafLog()

  final private class LeafLog() extends Leaf("histmersion", "log", Help) with SharedFlags:
    private val trace_ =
      value[LogEvent.Trace]("trace", "One trace: every step of one request or job (an answer's Histmersion-Trace)")
    private val job_ = value[LogEvent.Key]("job", "What happened to this job (its id)")
    private val run_ = value[LogEvent.Key]("run", "What happened to this agent run (its id)")
    private val source_ = value[LogEvent.Key]("source", "What happened to this source (its citekey)")
    private val problem_ =
      value[LogEvent.ProblemId](
        "problem",
        "A problem's id (the one an answer named); alone: every step of the trace it was raised in"
      )
    private val label_ = value[LogEvent.Label]("label", "The label a client gave its calls (Histmersion-Trace-Label)")
    private val kind_ = value[LogEvent.Kind]("kind", s"Only: ${LogEvent.Kinds.mkString(", ")}")
    private val since_ = value[TimeBound]("since", "From: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago")
    private val until_ = value[TimeBound]("until", "Up to: a date or an ISO instant")
    private val limit_ =
      value[LogPage.Limit]("limit", s"Events (default ${LogPage.DefaultLimit}, at most ${LogPage.MaxLimit})")
    private val before_ = value[LogPage.Cursor]("before", "The next older page: the cursor the last page printed")
    private val file_ = flag("file", "Read the server's log file, not the database (works while the database is down)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      LogCmd.Command.Log(
        LogPage.Filter(
          trace = trace_.toOption,
          label = label_.toOption,
          job = job_.toOption,
          run = run_.toOption,
          source = source_.toOption,
          problem = problem_.toOption,
          kind = kind_.toOption,
          since = since_.toOption,
          until = until_.toOption,
          limit = limit_.toOption,
          before = before_.toOption,
          from = Option.when(file_.getOrElse(false))(LogPage.From.File)
        ),
        json_()
      )
    )
