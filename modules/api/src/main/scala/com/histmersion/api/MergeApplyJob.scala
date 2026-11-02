package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Left is always here and right always the bundle: JabRef's vocabulary (`keep-left`, `keep-right`, `both`), which the
  * resolution file uses.
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

  val JobKind = com.histmersion.util.data.exchange.ExchangeJobKinds.MergeApply.kind.value

  val InterruptedText: String =
    "the server stopped while this merge apply ran — its transaction was committed or rolled back as a whole, " +
      "never half applied; `histmersion merge list` shows whether it was applied, and planning the bundle again " +
      "shows what is left"

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
      case JobState.Failed | JobState.Paused | JobState.Cancelled =>
        MergeApplyJob(id, MergeJobState.Failed, bundle, planHash, elapsedMs, error = error)
