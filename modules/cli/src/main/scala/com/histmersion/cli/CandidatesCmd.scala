package com.histmersion
package cli

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

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Sweep(goal: String, writes: Cli.Writes)
    case Mark(ids: List[Long], as: String)
    case Listing(goal: Option[String], state: Option[String], limit: Option[Int], offset: Option[Int])

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Sweep(goal, writes) =>
          c.call(AcquireApi.sweepCandidates)(
            CandidateSweep.Request(CandidateSweep.Request.Goal(goal), CandidateSweep.Request.DryRun(writes.dry))
          ).map(s => Cli.out.println(CandidatesMarkdown.sweep(s)))
        case Command.Mark(ids, as) =>
          c.call(AcquireApi.markCandidates)(
            CandidateMark(
              ids.toList.map(Source.Candidate.Id(_)),
              Source.Candidate.State(as)
            )
          ).map(m => Cli.out.println(CandidatesMarkdown.marked(m)))
        case Command.Listing(goal, state, limit, offset) =>
          c.call(AcquireApi.candidates)(
            (
              goal.map(Goal.Slug(_)),
              state.map(Candidates.State.Wire(_)),
              limit.map(AcquireApi.Limit(_)),
              offset.map(CandidateList.Offset(_))
            )
          ).map(l => Cli.out.println(CandidatesMarkdown.list(l)))
