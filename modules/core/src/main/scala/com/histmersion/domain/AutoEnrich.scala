package com.histmersion.domain

import com.histmersion.api.Plural
import com.histmersion.api.given
import com.histmersion.domain.automation.Run

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

  /** Why no run is queued. `Off` is silent: nothing was asked for. */
  type DecideAgentsEnabled = DecideAgentsEnabled.T
  object DecideAgentsEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideSwitchOn = DecideSwitchOn.T
  object DecideSwitchOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideFindings = _root_.com.histmersion.domain.AutoEnrich.EnrichedByFindings
  val DecideFindings: _root_.com.histmersion.domain.AutoEnrich.EnrichedByFindings.type = _root_.com.histmersion.domain.AutoEnrich.EnrichedByFindings
  type DecideNotes = _root_.com.histmersion.domain.AutoEnrich.EnrichedByNotes
  val DecideNotes: _root_.com.histmersion.domain.AutoEnrich.EnrichedByNotes.type = _root_.com.histmersion.domain.AutoEnrich.EnrichedByNotes
  type EnrichedByResult = EnrichedByResult.T
  object EnrichedByResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnrichedByFindings = EnrichedByFindings.T
  object EnrichedByFindings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnrichedByNotes = EnrichedByNotes.T
  object EnrichedByNotes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueuedResult = QueuedResult.T
  object QueuedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotQueuedResult = NotQueuedResult.T
  object NotQueuedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotQueuedWhy = NotQueuedWhy.T
  object NotQueuedWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagedResult = ManagedResult.T
  object ManagedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagedItem = ManagedItem.T
  object ManagedItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagedManagerState = ManagedManagerState.T
  object ManagedManagerState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteResult = NoteResult.T
  object NoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Skip:
    case Off
    case Enriched(why: Skip.Enriched.Why)

  object Skip:
    object Enriched:
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One earlier `enrich-source` run of the source: its id and state. */
  final case class PriorRun(id: PriorRun.Id, state: Run.State)

  object PriorRun:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Whether to queue; Left = why not. */
  def decide(
      agentsEnabled: com.histmersion.domain.AutoEnrich.DecideAgentsEnabled,
      switchOn: com.histmersion.domain.AutoEnrich.DecideSwitchOn,
      findings: com.histmersion.domain.AutoEnrich.DecideFindings,
      notes: Option[com.histmersion.domain.AutoEnrich.DecideNotes],
      runs: List[PriorRun]
  ): Either[Skip, Unit] =
    if !agentsEnabled || !switchOn then Left(Skip.Off)
    else enrichedBy(findings, notes, runs).map(Skip.Enriched(_)).toLeft(())

  /** Why the source counts as already enriched, if it does. */
  def enrichedBy(findings: com.histmersion.domain.AutoEnrich.EnrichedByFindings, notes: Option[com.histmersion.domain.AutoEnrich.EnrichedByNotes], runs: List[PriorRun]): Option[com.histmersion.domain.AutoEnrich.EnrichedByResult] =
    val live = runs.find(r => r.state != Run.State.Failed && r.state != Run.State.Cancelled)
    if findings > 0 then Some(s"it already has ${Plural.count(findings, "finding")}")
    else if notes.exists(_.trim.nonEmpty) then Some("it already has notes")
    else live.map(r => s"enrich-source run ${r.id} is ${r.state.wire}")

  /** The job's progress note for what happened. */
  def queued(runId: com.histmersion.domain.automation.Run.Id): com.histmersion.domain.AutoEnrich.QueuedResult = s"enrichment queued: run $runId"
  def notQueued(why: com.histmersion.domain.AutoEnrich.NotQueuedWhy): com.histmersion.domain.AutoEnrich.NotQueuedResult = s"enrichment not queued: $why"

  /** The note when the source went to the enrich manager (docs/research-process-managers.md, phase 2): its item, and —
    * unless the manager is active — that nothing runs until a person resumes it.
    */
  def managed(item: com.histmersion.domain.AutoEnrich.ManagedItem, managerState: com.histmersion.domain.AutoEnrich.ManagedManagerState): com.histmersion.domain.AutoEnrich.ManagedResult =
    s"enrichment queued: item #$item of the enrich manager" +
      (if managerState == "active" then "" else s" ($managerState — resume it in the Managers window to start)")

  def note(skip: Skip): Option[com.histmersion.domain.AutoEnrich.NoteResult] = skip match
    case Skip.Off           => None
    case Skip.Enriched(why) => Some(notQueued(why))
