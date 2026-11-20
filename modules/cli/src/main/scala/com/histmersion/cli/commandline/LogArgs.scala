package com.histmersion
package cli
package commandline

import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*

/** The command line of [[LogCmd]]. */
object LogArgs:
  private val Help = "What happened, step by step: answered requests, provider calls, problems and database writes, " +
    "each with the trace that links one request's or job's steps"

  object LogEntry extends CommandLine.Entry:
    val name = "log"
    def help: String = Help
    def conf(): Subcommand = LeafLog()

  final private class LeafLog() extends Leaf("histmersion", "log", Help) with SharedFlags:
    private val trace_ =
      value[String]("trace", "One trace: every step of one request or job (an answer's Histmersion-Trace)")
    private val job_ = value[String]("job", "What happened to this job (its id)")
    private val run_ = value[String]("run", "What happened to this agent run (its id)")
    private val source_ = value[String]("source", "What happened to this source (its citekey)")
    private val problem_ =
      value[String](
        "problem",
        "A problem's id (the one an answer named); alone: every step of the trace it was raised in"
      )
    private val label_ = value[String]("label", "The label a client gave its calls (Histmersion-Trace-Label)")
    private val kind_ = value[String]("kind", s"Only: ${LogEvent.Kinds.mkString(", ")}")
    private val since_ = value[String]("since", "From: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago")
    private val until_ = value[String]("until", "Up to: a date or an ISO instant")
    private val limit_ = value[Int]("limit", s"Events (default ${LogPage.DefaultLimit}, at most ${LogPage.MaxLimit})")
    private val before_ = value[String]("before", "The next older page: the cursor the last page printed")
    private val file_ = flag("file", "Read the server's log file, not the database (works while the database is down)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      LogCmd.Command.Log(
        LogPage.Filter(
          trace = trace_.toOption.map(LogEvent.Trace(_)),
          label = label_.toOption.map(LogEvent.Label(_)),
          job = job_.toOption.map(LogEvent.Key(_)),
          run = run_.toOption.map(LogEvent.Key(_)),
          source = source_.toOption.map(LogEvent.Key(_)),
          problem = problem_.toOption.map(LogEvent.Key(_)),
          kind = kind_.toOption.map(LogEvent.Kind(_)),
          since = since_.toOption.map(LogEvent.At(_)),
          until = until_.toOption.map(LogEvent.At(_)),
          limit = limit_.toOption.map(LogPage.Limit(_)),
          before = before_.toOption.map(LogPage.Cursor(_)),
          from = Option.when(file_.getOrElse(false))(LogPage.From("file"))
        ),
        json_()
      )
    )
