package com.histmersion
package cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.api.library.{AcquireApi, CandidateMark, Candidates, CandidatesMarkdown}
import com.histmersion.api.library.CandidateSweep
import com.histmersion.util.console.Console
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.CandidateList
import com.histmersion.api.library.Source

/** `histmersion candidates` (#3761): the new sources the deterministic search found for the research goals — list them,
  * sweep a goal's watches now, mark what was decided. A pure HTTP client of `AcquireApi`.
  */
object CandidatesCmd:

  private def listCmd (using console: Console) =
    (
      Opts.option[String]("goal", "One goal's candidates (default: every goal)").orNone,
      Opts
        .option[String]("state", s"${Candidates.State.values.map(_.wire).mkString("|")} (default new: the unseen)")
        .orNone,
      Opts.option[Int]("limit", "Max results").orNone,
      Opts.option[Int]("offset", "Skip this many (the list's footer names the next offset)").orNone
    )
      .mapN { (goal, state, limit, offset) => (c: Client) =>
        c.call(AcquireApi.candidates)(
          (
            goal.map(Goal.Slug(_)),
            state.map(Candidates.State.Wire(_)),
            limit.map(AcquireApi.Limit(_)),
            offset.map(CandidateList.Offset(_))
          )
        ).map(l => Cli.out.println(CandidatesMarkdown.list(l)))
      }

  private def sweepCmd (using console: Console) = Opts.subcommand(
    "sweep",
    "Read a goal's saved queries, citation and author alerts from their watermarks now (no model)"
  ) {
    (Opts.argument[String]("goal"), Opts.flag("dry-run", "Read, store nothing (no candidates, no watermarks)").orFalse)
      .mapN { (goal, dry) => (c: Client) =>
        c.call(AcquireApi.sweepCandidates)(CandidateSweep.Request(CandidateSweep.Request.Goal(goal), CandidateSweep.Request.DryRun(dry)))
          .map(s => Cli.out.println(CandidatesMarkdown.sweep(s)))
      }
  }

  private def markCmd (using console: Console) =
    Opts.subcommand("mark", "Record a decision on candidates by id: seen, queued, dismissed (or new again)") {
      (
        Opts.arguments[Long]("id"),
        Opts.option[String]("as", "seen|queued|dismissed|new").withDefault(Candidates.State.Seen.wire.value)
      )
        .mapN { (ids, as) => (c: Client) =>
          c.call(AcquireApi.markCandidates)(CandidateMark(
              ids.toList.map(Source.Candidate.Id(_)),
              Source.Candidate.State(as)
            ))
            .map(m => Cli.out.println(CandidatesMarkdown.marked(m)))
        }
    }

  def command(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("candidates", "New sources found for the research goals without a model: list, sweep, mark") {
      sweepCmd orElse markCmd orElse listCmd
    }
