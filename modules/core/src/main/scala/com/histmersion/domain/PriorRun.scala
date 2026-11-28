package com.histmersion
package domain

import com.histmersion.api.given
import com.histmersion.domain.automation.Run
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Plural
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.util.problem.Problems
import com.histmersion.util.problem.Text.sentences
import com.histmersion.domain.automation.Manager

final case class PriorRun(id: PriorRun.Id, state: Run.State)

object PriorRun:
  type Id = Id.T
  object Id extends Newtype[String, Pure]

  type FindingCount = FindingCount.T

  object FindingCount extends Newtype[Int, Pure]

  type ItemId = ItemId.T

  object ItemId extends Newtype[Long, Pure]

  type Note = Note.T

  object Note extends Newtype[String, Pure]

  type Notes = Notes.T

  object Notes extends Newtype[String, Pure]

  enum Skip derives CanEqual:
    case Off
    case Enriched(why: Rationale)

  enum Rationale:
    /** It has `findings` findings. */
    case HasFindings(findings: FindingCount)

    /** It has notes. */
    case HasNotes

    /** Its earlier `enrich-source` run `run` is `state` (neither failed nor cancelled). */
    case RunLive(run: PriorRun.Id, state: Run.State)

    /** No source has the citekey. */
    case NoSource

    /** The account as a job's progress note and the manager's items show it. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def said: String = this match
      case HasFindings(findings) =>
        s"it already has ${Plural.count(Plural.CountN(findings.value.toLong), Plural.CountSingular("finding"))}"
      case HasNotes            => "it already has notes"
      case RunLive(run, state) => s"enrich-source run $run is ${state.wire}"
      case NoSource            => "no such source"

    /** The job's progress note: enrichment is not queued, and why. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def notQueued: Note = Note(s"enrichment not queued: $said")

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def decide(
      agentsEnabled: Boolean,
      switchOn: Boolean,
      findings: FindingCount,
      notes: Option[Notes],
      runs: List[PriorRun]
  ): Either[Skip, Unit] =
    if !agentsEnabled || !switchOn then Left(Skip.Off)
    else enrichedBy(findings, notes, runs).map(Skip.Enriched(_)).toLeft(())

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def enrichedBy(findings: FindingCount, notes: Option[Notes], runs: List[PriorRun]): Option[Rationale] =
    val live = runs.find(r => r.state != Run.State.Failed && r.state != Run.State.Cancelled)
    if findings.value > 0 then Some(Rationale.HasFindings(findings))
    else if notes.exists(_.value.trim.nonEmpty) then Some(Rationale.HasNotes)
    else live.map(r => Rationale.RunLive(r.id, r.state))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def queued(runId: Run.Id): Note = Note(s"enrichment queued: run $runId")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def notQueued(problems: Problems): Note = Note(s"enrichment not queued: ${problems.sentences.mkString("; ")}")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def managed(item: ItemId, managerState: Manager.State.Wire): Note =
    Note(
      s"enrichment queued: item #$item of the enrich manager" +
        (if managerState.value == "active" then "" else s" ($managerState — resume it in the Managers window to start)")
    )

  def note(skip: Skip): Option[Note] = skip match
    case Skip.Off           => None
    case Skip.Enriched(why) => Some(why.notQueued)
