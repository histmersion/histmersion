package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.jobs.JobState
import com.histmersion.api.given

/** Left is always here and right always the bundle: JabRef's vocabulary (`keep-left`, `keep-right`, `both`), which the
  * resolution file uses.
  */

final case class MergeApplyJob(
    job: MergeApplyJob.Job,
    state: MergeJobState,
    bundle: MergeApplyJob.Bundle,
    planHash: MergeApplyJob.PlanHash,
    elapsedMs: MergeApplyJob.ElapsedMs,
    result: Option[MergeApplyResult] = None,
    error: Option[MergeApplyJob.Error] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object MergeApplyJob:


  type Job = Job.T
  object Job extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bundle = Bundle.T
  object Bundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanHash = PlanHash.T
  object PlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ElapsedMs = ElapsedMs.T
  object ElapsedMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Error = Error.T
  object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val JobKind = com.histmersion.api.exchange.ExchangeJobKinds.MergeApply.kind.value

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
