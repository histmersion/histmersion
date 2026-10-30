package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `merge plan` (docs/research-merge-import.md §5–§6, milestone M3): what importing a bundle into this collection would
  * do, and every question it raises, in ONE run.
  *
  * Sides: **left** is always HERE (this collection), **right** is always the BUNDLE (the other one) — JabRef's
  * vocabulary (`keep-left`, `keep-right`, `both`), as the resolution file uses it.
  *
  * A plan is pure: computed from the bundle, this collection's state and the resolutions, and deterministic. Issue ids
  * are stable while the records they name do not change (§6.1), so a resolution file can be written once and reused; an
  * id that is no longer in the plan is reported as stale, never silently re-applied.
  */

/** `POST /api/merge/apply` and `GET /api/merge/apply/{job}`: the apply runs in the background (its backup can take
  * minutes on a large collection); the request waits up to `merge.apply-wait-seconds` and answers where it is. `result`
  * once it is done — refused or applied, both are `done`; `error` when it crashed or was interrupted. The job is a
  * `jobs` row (kind [[MergeApplyJob.JobKind]]), so it outlives a restart (#888).
  */
final case class MergeApplyJob(
    job: String,
    state: MergeJobState,
    bundle: String,
    planHash: String,
    elapsedMs: Long,
    result: Option[MergeApplyResult] = None,
    error: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeApplyJob:
  given Schema[MergeApplyJob] = summon[KindlingsSchema[MergeApplyJob]].schema

  /** The `jobs.kind` of a merge apply. */
  val JobKind = com.histmersion.api.JobKind.MergeApply.wire

  /** What an interrupted apply says. */
  val InterruptedText: String =
    "the server stopped while this merge apply ran — its transaction was committed or rolled back as a whole, " +
      "never half applied; `histmersion merge list` shows whether it was applied, and planning the bundle again " +
      "shows what is left"

  /** A stored apply as the API answers it: the `jobs` row's state (a `failed` row whose error is
    * [[JobState.Interrupted]] was cut short by a restart), its bundle and plan, and the result it recorded. Pure.
    */
  def of(
      id: String,
      status: JobState,
      bundle: String,
      planHash: String,
      elapsedMs: Long,
      error: Option[String],
      result: Option[MergeApplyResult]
  ): MergeApplyJob =
    status match
      case JobState.Queued | JobState.Running => MergeApplyJob(id, MergeJobState.Running, bundle, planHash, elapsedMs)
      case JobState.Done                      =>
        result.fold(
          MergeApplyJob(
            id,
            MergeJobState.Failed,
            bundle,
            planHash,
            elapsedMs,
            error = Some(
              "the merge apply finished but its result was not recorded — `histmersion merge list` shows what was applied"
            )
          )
        )(r => MergeApplyJob(id, MergeJobState.Done, bundle, planHash, elapsedMs, result = Some(r)))
      case JobState.Failed if error.contains(JobState.Interrupted) =>
        MergeApplyJob(id, MergeJobState.Interrupted, bundle, planHash, elapsedMs, error = Some(InterruptedText))
      // a merge apply is never paused or cancelled: one in either state did not apply
      case JobState.Failed | JobState.Paused | JobState.Cancelled =>
        MergeApplyJob(id, MergeJobState.Failed, bundle, planHash, elapsedMs, error = error)
