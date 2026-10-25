package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.api.given
import com.histmersion.values.RunState

/** `agents.auto-enrich`: after an ingest job stores a source, an `enrich-source` run is queued for it
  * — unless agents are off, the switch is off, or the source is ALREADY ENRICHED. Pure: the server
  * gathers the facts, this decides and words the job's progress note.
  *
  * Already enriched means one of:
  *   - the source has at least one finding, or non-blank notes. Those are what enrichment writes and
  *     what no import writes (a DOI ingest stores the abstract as the summary, Calibre its comments and
  *     tags, so a summary or tags prove nothing);
  *   - an `enrich-source` run for it exists that has not failed or been cancelled — queued, running,
  *     paused, waiting or done. So a second import of the same file, or a re-OCR, queues nothing while
  *     the first run is pending, and nothing after it finished; a failed run does not count, so the
  *     next import tries again. */
object AutoEnrich:

  /** Why no run is queued. `Off` is silent: nothing was asked for. */
  enum Skip:
    case Off
    case Enriched(why: String)

  /** One earlier `enrich-source` run of the source: its id and state. */
  final case class PriorRun(id: String, state: RunState)

  /** Whether to queue; Left = why not. */
  def decide(agentsEnabled: Boolean, switchOn: Boolean, findings: Int, notes: Option[String],
      runs: List[PriorRun]): Either[Skip, Unit] =
    if !agentsEnabled || !switchOn then Left(Skip.Off)
    else enrichedBy(findings, notes, runs).map(Skip.Enriched(_)).toLeft(())

  /** Why the source counts as already enriched, if it does. */
  def enrichedBy(findings: Int, notes: Option[String], runs: List[PriorRun]): Option[String] =
    val live = runs.find(r => r.state != RunState.Failed && r.state != RunState.Cancelled)
    if findings > 0 then Some(s"it already has ${Plural.count(findings, "finding")}")
    else if notes.exists(_.trim.nonEmpty) then Some("it already has notes")
    else live.map(r => s"enrich-source run ${r.id} is ${r.state.wire}")

  /** The job's progress note for what happened. */
  def queued(runId: String): String = s"enrichment queued: run $runId"
  def notQueued(why: String): String = s"enrichment not queued: $why"

  /** The note when the source went to the enrich manager (docs/research-process-managers.md, phase 2):
    * its item, and — unless the manager is active — that nothing runs until a person resumes it. */
  def managed(item: Long, managerState: String): String =
    s"enrichment queued: item #$item of the enrich manager" +
      (if managerState == "active" then "" else s" ($managerState — resume it in the Managers window to start)")

  def note(skip: Skip): Option[String] = skip match
    case Skip.Off           => None
    case Skip.Enriched(why) => Some(notQueued(why))
