package com.histmersion
package domain

import com.histmersion.api.Plural
import com.histmersion.api.given
import com.histmersion.domain.automation.Run
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.automation.Manager

/** `agents.auto-enrich`: after an ingest job stores a source, an `enrich-source` run is queued for it — unless agents
  * are off, the switch is off, or the source is ALREADY ENRICHED. Pure: the server gathers the facts, this decides and
  * words the job's progress note.
  *
  * Already enriched means one of:
  *   - the source has at least one finding, or non-blank notes. Those are what enrichment writes and what no import
  *     writes (a DOI ingest stores the abstract as the summary, Calibre its comments and tags, so a summary or tags
  *     prove nothing);
  *   - an `enrich-source` run for it exists that has not failed or been cancelled — queued, running, paused, waiting or
  *     done. So a second import of the same file, or a re-OCR, queues nothing while the first run is pending, and
  *     nothing after it finished; a failed run does not count, so the next import tries again.
  */
object AutoEnrich:
  type FindingCount = FindingCount.T
  object FindingCount extends Newtype[Int, Pure]
  type ItemId = ItemId.T
  object ItemId extends Newtype[Long, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
  type Notes = Notes.T
  object Notes extends Newtype[String, Pure]
  type Reason = Reason.T
  object Reason extends Newtype[String, Pure]

  /** Why no run is queued. `Off` is silent: nothing was asked for. */

  enum Skip derives CanEqual:
    case Off
    case Enriched(why: Skip.Enriched.Why)

  object Skip:
    object Enriched:
      export AutoEnrich.{Reason as Why}

  /** One earlier `enrich-source` run of the source: its id and state. */
  final case class PriorRun(id: PriorRun.Id, state: Run.State)

  object PriorRun:
    type Id = Id.T
    object Id extends Newtype[String, Pure]

  /** Whether to queue; Left = why not. */
  def decide(
      agentsEnabled: Boolean,
      switchOn: Boolean,
      findings: FindingCount,
      notes: Option[Notes],
      runs: List[PriorRun]
  ): Either[Skip, Unit] =
    if !agentsEnabled || !switchOn then Left(Skip.Off)
    else enrichedBy(findings, notes, runs).map(Skip.Enriched(_)).toLeft(())

  /** Why the source counts as already enriched, if it does. */
  def enrichedBy(findings: FindingCount, notes: Option[Notes], runs: List[PriorRun]): Option[Reason] =
    val live = runs.find(r => r.state != Run.State.Failed && r.state != Run.State.Cancelled)
    if findings.value > 0 then
      Some(Reason(s"it already has ${Plural.count(Plural.CountN(findings.value.toLong), Plural.CountSingular("finding"))}"))
    else if notes.exists(_.value.trim.nonEmpty) then Some(Reason("it already has notes"))
    else live.map(r => Reason(s"enrich-source run ${r.id} is ${r.state.wire}"))

  /** The job's progress note for what happened. */
  def queued(runId: Run.Id): Note = Note(s"enrichment queued: run $runId")
  def notQueued(why: Reason): Note = Note(s"enrichment not queued: $why")

  /** The note when the source went to the enrich manager (docs/research-process-managers.md, phase 2): its item, and —
    * unless the manager is active — that nothing runs until a person resumes it.
    */
  def managed(item: ItemId, managerState: Manager.State.Wire): Note =
    Note(
      s"enrichment queued: item #$item of the enrich manager" +
        (if managerState.value == "active" then "" else s" ($managerState — resume it in the Managers window to start)")
    )

  def note(skip: Skip): Option[Note] = skip match
    case Skip.Off           => None
    case Skip.Enriched(why) => Some(notQueued(why))
