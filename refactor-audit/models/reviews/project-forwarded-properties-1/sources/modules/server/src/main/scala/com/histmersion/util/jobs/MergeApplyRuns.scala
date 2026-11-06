package com.histmersion.util.jobs

import com.histmersion.util.database.Transactor

import com.histmersion.api.jobs.JobState
import com.histmersion.api.exchange.{MergeApplyJob, MergeApplyResult}

import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** The `jobs` rows of merge applies (kind `merge-apply`): one is `running` from its start (it runs on its own thread,
  * never queued), with its bundle and its plan; it ends `done` with its report or `failed` with why. A restart fails an
  * unfinished one as `interrupted`, like every other job.
  */
trait MergeApplyRuns:
  /** Is a merge apply running? One at a time. */
  def running: com.histmersion.util.jobs.MergeApplyRuns.RunningResult

  /** A new running row for the bundle and plan; its id. */
  def start(bundle: com.histmersion.util.jobs.MergeApplyRuns.StartBundle, planHash: com.histmersion.util.jobs.MergeApplyRuns.StartPlanHash): com.histmersion.util.jobs.MergeApplyRuns.StartResult

  /** How the run ended; a row a restart already failed is left as it is. */
  def finish(id: com.histmersion.util.jobs.MergeApplyRuns.FinishId, outcome: Either[com.histmersion.util.jobs.MergeApplyRuns.FinishOutcomePart1, MergeApplyResult]): Unit

  /** One merge apply as the API answers it; `None` for an unknown id (or another kind of job). */
  def status(id: com.histmersion.util.jobs.MergeApplyRuns.StatusId): Option[MergeApplyJob]

object MergeApplyRuns:
  type RunningResult = RunningResult.T
  object RunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartResult = StartResult.T
  object StartResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBundle = StartBundle.T
  object StartBundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartPlanHash = StartPlanHash.T
  object StartPlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinishId = FinishId.T
  object FinishId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinishOutcomePart1 = FinishOutcomePart1.T
  object FinishOutcomePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusId = StatusId.T
  object StatusId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(ds: DataSource): MergeApplyRuns = Implementation(ds)

  final private class Implementation(ds: DataSource) extends MergeApplyRuns:
    def running: com.histmersion.util.jobs.MergeApplyRuns.Implementation.RunningResult = Transactor(ds).read(JobRepo.mergeApplyRunning)
    def start(bundle: com.histmersion.util.jobs.MergeApplyRuns.Implementation.StartBundle, planHash: com.histmersion.util.jobs.MergeApplyRuns.Implementation.StartPlanHash): com.histmersion.util.jobs.MergeApplyRuns.Implementation.StartResult =
      Transactor(ds).transact(JobRepo.startMergeApply(bundle, planHash))
    def finish(id: com.histmersion.util.jobs.MergeApplyRuns.Implementation.FinishId, outcome: Either[com.histmersion.util.jobs.MergeApplyRuns.Implementation.FinishOutcomePart1, MergeApplyResult]): Unit =
      Transactor(ds).transact(JobRepo.finishMergeApply(id, outcome))
    def status(id: com.histmersion.util.jobs.MergeApplyRuns.Implementation.StatusId): Option[MergeApplyJob] = Transactor(ds).read(JobRepo.mergeApply(id))

  object Implementation:
    type RunningResult = RunningResult.T
    object RunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartResult = StartResult.T
    object StartResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartBundle = StartBundle.T
    object StartBundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartPlanHash = StartPlanHash.T
    object StartPlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FinishId = FinishId.T
    object FinishId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FinishOutcomePart1 = FinishOutcomePart1.T
    object FinishOutcomePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StatusId = StatusId.T
    object StatusId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One run as the in-memory twin keeps it. */
  final case class Run(bundle: Run.Bundle, planHash: Run.PlanHash, outcome: Option[Either[Run.OutcomePart1, MergeApplyResult]])

  object Run:
    type Bundle = Bundle.T
    object Bundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanHash = PlanHash.T
    object PlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OutcomePart1 = OutcomePart1.T
    object OutcomePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The rows in memory, keyed by id; ids are numbered in start order. */
  final class InMemory(state: AtomicReference[Map[InMemory.StateKey, Run]] = AtomicReference(Map.empty[String, Run]))
      extends MergeApplyRuns:
    def running: com.histmersion.util.jobs.MergeApplyRuns.InMemory.RunningResult = state.get().values.exists(_.outcome.isEmpty)

    def start(bundle: com.histmersion.util.jobs.MergeApplyRuns.InMemory.StartBundle, planHash: com.histmersion.util.jobs.MergeApplyRuns.InMemory.StartPlanHash): com.histmersion.util.jobs.MergeApplyRuns.InMemory.StartResult =
      val next = state.updateAndGet(m => m + (s"merge-apply-${m.size + 1}" -> Run(bundle, planHash, None)))
      s"merge-apply-${next.size}"

    def finish(id: com.histmersion.util.jobs.MergeApplyRuns.InMemory.FinishId, outcome: Either[com.histmersion.util.jobs.MergeApplyRuns.InMemory.FinishOutcomePart1, MergeApplyResult]): Unit =
      val _ = state.updateAndGet(m =>
        m.get(id).filter(_.outcome.isEmpty).fold(m)(r => m + (id -> r.modify(_.outcome).setTo(Some(outcome))))
      )

    def status(id: com.histmersion.util.jobs.MergeApplyRuns.InMemory.StatusId): Option[MergeApplyJob] =
      state.get().get(id.trim).map { r =>
        r.outcome match
          case None =>
            MergeApplyJob(id.trim, com.histmersion.api.exchange.MergeJobState.Running, r.bundle, r.planHash, 0)
          case Some(Right(a)) => MergeApplyJob.of(id.trim, JobState.Done, r.bundle, r.planHash, 0, None, Some(a))
          case Some(Left(e))  => MergeApplyJob.of(id.trim, JobState.Failed, r.bundle, r.planHash, 0, Some(e), None)
      }

  object InMemory:
    type StateKey = StateKey.T
    object StateKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type RunningResult = RunningResult.T
    object RunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartResult = StartResult.T
    object StartResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartBundle = _root_.com.histmersion.util.jobs.MergeApplyRuns.Run.Bundle
    val StartBundle: _root_.com.histmersion.util.jobs.MergeApplyRuns.Run.Bundle.type = _root_.com.histmersion.util.jobs.MergeApplyRuns.Run.Bundle
    type StartPlanHash = _root_.com.histmersion.util.jobs.MergeApplyRuns.Run.PlanHash
    val StartPlanHash: _root_.com.histmersion.util.jobs.MergeApplyRuns.Run.PlanHash.type = _root_.com.histmersion.util.jobs.MergeApplyRuns.Run.PlanHash
    type FinishId = FinishId.T
    object FinishId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FinishOutcomePart1 = FinishOutcomePart1.T
    object FinishOutcomePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StatusId = StatusId.T
    object StatusId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
