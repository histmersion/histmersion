package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.jobs.JobState
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.SpatialHierarchyResult

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
  type InterruptedMessage = InterruptedMessage.T
  object InterruptedMessage extends RefinedType[String, Pure]


  type InterruptedText = InterruptedTextValue
  type InterruptedTextValue = InterruptedMessage
  val InterruptedTextValue: InterruptedMessage.type = InterruptedMessage
  export com.histmersion.api.exchange.Bundle.{Name as OfBundle}
  export MergePlan.{PlanHash as OfPlanHash}
  type OfElapsedMs = ElapsedMs
  val OfElapsedMs: ElapsedMs.type = ElapsedMs
  type OfError = Error
  val OfError: Error.type = Error

  export com.histmersion.api.SpatialHierarchyResult.Job.{Id as Job}
  export com.histmersion.api.exchange.Bundle.{Name as Bundle}
  export MergePlan.PlanHash
  type ElapsedMs = ElapsedMs.T
  object ElapsedMs extends RefinedType[Long, Pure]
  type Error = Error.T
  object Error extends RefinedType[String, Pure]

  val JobKind = ExchangeJobKinds.MergeApply.kind.value

  val InterruptedText: InterruptedText =
    InterruptedMessage("the server stopped while this merge apply ran — its transaction was committed or rolled back as a whole, " +
      "never half applied; `histmersion merge list` shows whether it was applied, and planning the bundle again " +
      "shows what is left")

  def of(
      id: SpatialHierarchyResult.Job.Id,
      status: JobState,
      bundle: MergeApplyJob.OfBundle,
      planHash: MergeApplyJob.OfPlanHash,
      elapsedMs: OfElapsedMs,
      error: Option[OfError],
      result: Option[MergeApplyResult]
  ): MergeApplyJob =
    status match
      case JobState.Queued | JobState.Running => MergeApplyJob(SpatialHierarchyResult.Job.Id((id).value), MergeJobState.Running, bundle, planHash, elapsedMs)
      case JobState.Done                      =>
        result.fold(
          MergeApplyJob(
            SpatialHierarchyResult.Job.Id((id).value),
            MergeJobState.Failed,
            bundle,
            planHash,
            elapsedMs,
            error = Some(
              Error("the merge apply finished but its result was not recorded — `histmersion merge list` shows what was applied")
            )
          )
        )(r => MergeApplyJob(SpatialHierarchyResult.Job.Id((id).value), MergeJobState.Done, bundle, planHash, elapsedMs, result = Some(r)))
      case JobState.Failed if error.contains(JobState.Interrupted) =>
        MergeApplyJob(SpatialHierarchyResult.Job.Id((id).value), MergeJobState.Interrupted, bundle, planHash, elapsedMs, error = Some(Error((InterruptedText).value)))
      case JobState.Failed | JobState.Paused | JobState.Cancelled =>
        MergeApplyJob(SpatialHierarchyResult.Job.Id((id).value), MergeJobState.Failed, bundle, planHash, elapsedMs, error = error)
