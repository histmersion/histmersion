package com.histmersion
package api.exchange

import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.jobs.JobState
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
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
    error: Option[Problem] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema
object MergeApplyJob:

  export com.histmersion.api.exchange.Bundle.Name as OfBundle
  export MergePlan.PlanHash as OfPlanHash
  type OfElapsedMs = ElapsedMs
  val OfElapsedMs: ElapsedMs.type = ElapsedMs

  export com.histmersion.api.SpatialHierarchyResult.Job.Id as Job
  export com.histmersion.api.exchange.Bundle.Name as Bundle
  export MergePlan.PlanHash
  type ElapsedMs = ElapsedMs.T
  object ElapsedMs extends Newtype[Long, Pure]

  val JobKind = ExchangeJobKinds.MergeApply.kind.value

  /** Why a stored row failed: a restart cut it short (the store keeps that as its own sentence), or it ended with a
    * reason of its own.
    */
  enum Failure derives CanEqual:
    case Interrupted
    case Other(problem: Problem)

  /** A restart cut the apply short: its transaction was committed or rolled back as a whole, never half applied. */
  val InterruptedText: Problem = Problem.Run.Interrupted

  def of(
      id: SpatialHierarchyResult.Job.Id,
      status: JobState,
      bundle: MergeApplyJob.OfBundle,
      planHash: MergeApplyJob.OfPlanHash,
      elapsedMs: OfElapsedMs,
      error: Option[Failure],
      result: Option[MergeApplyResult]
  ): MergeApplyJob =
    status match
      case JobState.Queued | JobState.Running =>
        MergeApplyJob(SpatialHierarchyResult.Job.Id((id).value), MergeJobState.Running, bundle, planHash, elapsedMs)
      case JobState.Done =>
        result.fold(
          MergeApplyJob(
            SpatialHierarchyResult.Job.Id((id).value),
            MergeJobState.Failed,
            bundle,
            planHash,
            elapsedMs,
            error = Some(Problem.NotFound.Entity(Problem.TypeName.named[MergeApplyResult], Problem.Raw(s"$id")))
          )
        )(r =>
          MergeApplyJob(
            SpatialHierarchyResult.Job.Id((id).value),
            MergeJobState.Done,
            bundle,
            planHash,
            elapsedMs,
            result = Some(r)
          )
        )
      case JobState.Failed if error.contains(Failure.Interrupted) =>
        MergeApplyJob(
          SpatialHierarchyResult.Job.Id((id).value),
          MergeJobState.Interrupted,
          bundle,
          planHash,
          elapsedMs,
          error = Some(InterruptedText)
        )
      case JobState.Failed | JobState.Paused | JobState.Cancelled =>
        MergeApplyJob(
          SpatialHierarchyResult.Job.Id((id).value),
          MergeJobState.Failed,
          bundle,
          planHash,
          elapsedMs,
          error = error.map {
            case Failure.Interrupted   => InterruptedText
            case Failure.Other(reason) => reason
          }
        )
