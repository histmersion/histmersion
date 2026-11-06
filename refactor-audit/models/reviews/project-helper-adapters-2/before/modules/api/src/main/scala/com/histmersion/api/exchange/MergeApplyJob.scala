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


  type InterruptedText = InterruptedText.T
  object InterruptedText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfId = OfId.T
  object OfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfBundle = _root_.com.histmersion.api.exchange.MergeApplyJob.Bundle
  val OfBundle: _root_.com.histmersion.api.exchange.MergeApplyJob.Bundle.type = _root_.com.histmersion.api.exchange.MergeApplyJob.Bundle
  type OfPlanHash = _root_.com.histmersion.api.exchange.MergeApplyJob.PlanHash
  val OfPlanHash: _root_.com.histmersion.api.exchange.MergeApplyJob.PlanHash.type = _root_.com.histmersion.api.exchange.MergeApplyJob.PlanHash
  type OfElapsedMs = _root_.com.histmersion.api.exchange.MergeApplyJob.ElapsedMs
  val OfElapsedMs: _root_.com.histmersion.api.exchange.MergeApplyJob.ElapsedMs.type = _root_.com.histmersion.api.exchange.MergeApplyJob.ElapsedMs
  type OfError = _root_.com.histmersion.api.exchange.MergeApplyJob.Error
  val OfError: _root_.com.histmersion.api.exchange.MergeApplyJob.Error.type = _root_.com.histmersion.api.exchange.MergeApplyJob.Error

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

  val InterruptedText: com.histmersion.api.exchange.MergeApplyJob.InterruptedText =
    _root_.com.histmersion.api.exchange.MergeApplyJob.InterruptedText("the server stopped while this merge apply ran — its transaction was committed or rolled back as a whole, " +
      "never half applied; `histmersion merge list` shows whether it was applied, and planning the bundle again " +
      "shows what is left")

  def of(
      id: com.histmersion.api.exchange.MergeApplyJob.OfId,
      status: JobState,
      bundle: com.histmersion.api.exchange.MergeApplyJob.OfBundle,
      planHash: com.histmersion.api.exchange.MergeApplyJob.OfPlanHash,
      elapsedMs: com.histmersion.api.exchange.MergeApplyJob.OfElapsedMs,
      error: Option[com.histmersion.api.exchange.MergeApplyJob.OfError],
      result: Option[MergeApplyResult]
  ): MergeApplyJob =
    status match
      case JobState.Queued | JobState.Running => MergeApplyJob(_root_.com.histmersion.api.exchange.MergeApplyJob.Job((id).value), MergeJobState.Running, bundle, planHash, elapsedMs)
      case JobState.Done                      =>
        result.fold(
          MergeApplyJob(
            _root_.com.histmersion.api.exchange.MergeApplyJob.Job((id).value),
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
        MergeApplyJob(_root_.com.histmersion.api.exchange.MergeApplyJob.Job((id).value), MergeJobState.Interrupted, bundle, planHash, elapsedMs, error = Some(InterruptedText))
      case JobState.Failed | JobState.Paused | JobState.Cancelled =>
        MergeApplyJob(_root_.com.histmersion.api.exchange.MergeApplyJob.Job((id).value), MergeJobState.Failed, bundle, planHash, elapsedMs, error = error)
