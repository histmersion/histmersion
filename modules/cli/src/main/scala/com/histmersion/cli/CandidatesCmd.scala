package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.api.library.{AcquireApi, CandidateMark, Candidates, CandidatesMarkdown}
import com.histmersion.api.library.CandidateSweep

/** `histmersion candidates` (#3761): the new sources the deterministic search found for the research goals — list them,
  * sweep a goal's watches now, mark what was decided. A pure HTTP client of `AcquireApi`.
  */
object CandidatesCmd:

  private val listCmd =
    (
      Opts.option[String]("goal", "One goal's candidates (default: every goal)").orNone,
      Opts
        .option[String]("state", s"${Candidates.State.values.map(_.wire).mkString("|")} (default new: the unseen)")
        .orNone,
      Opts.option[Int]("limit", "Max results").orNone,
      Opts.option[Int]("offset", "Skip this many (the list's footer names the next offset)").orNone
    )
      .mapN { (goal, state, limit, offset) => (c: Client) =>
        c.call(AcquireApi.candidates)((goal, state, limit, offset)).map(l => com.histmersion.cli.Cli.writeLine(CandidatesMarkdown.list(l)))
      }

  private val sweepCmd = Opts.subcommand(
    "sweep",
    "Read a goal's saved queries, citation and author alerts from their watermarks now (no model)"
  ) {
    (Opts.argument[String]("goal"), Opts.flag("dry-run", "Read, store nothing (no candidates, no watermarks)").orFalse)
      .mapN { (goal, dry) => (c: Client) =>
        c.call(AcquireApi.sweepCandidates)(CandidateSweep.Request(goal, dry))
          .map(s => com.histmersion.cli.Cli.writeLine(CandidatesMarkdown.sweep(s)))
      }
  }

  private val markCmd =
    Opts.subcommand("mark", "Record a decision on candidates by id: seen, queued, dismissed (or new again)") {
      (
        Opts.arguments[Long]("id"),
        Opts.option[String]("as", "seen|queued|dismissed|new").withDefault(Candidates.State.Seen.wire)
      )
        .mapN { (ids, as) => (c: Client) =>
          c.call(AcquireApi.markCandidates)(CandidateMark(ids.toList, as))
            .map(m => com.histmersion.cli.Cli.writeLine(CandidatesMarkdown.marked(m)))
        }
    }

  val command: Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("candidates", "New sources found for the research goals without a model: list, sweep, mark") {
      sweepCmd orElse markCmd orElse listCmd
    }
