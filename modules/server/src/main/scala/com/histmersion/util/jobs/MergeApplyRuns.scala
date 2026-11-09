package com.histmersion
package util.jobs

import com.histmersion.api.Problem
import com.histmersion.util.database.Transactor

import com.histmersion.api.jobs.JobState
import com.histmersion.api.exchange.{Bundle, MergeApplyJob, MergeApplyResult, MergePlan}
import com.histmersion.util.data.exchange.{MergeRepo, MergeRow}

import hearth.kindlings.optics.syntax.*

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.exchange.MergeJobState

/** The `jobs` rows of merge applies (kind `merge-apply`): one is `running` from its start (it runs on its own thread,
  * never queued), with its bundle and its plan; it ends `done` with its report or `failed` with why. A restart fails an
  * unfinished one as `interrupted`, like every other job.
  */
trait MergeApplyRuns:
  /** Is a merge apply running? One at a time. */
  def running: Boolean

  /** A new running row for the bundle and plan; its id. */
  def start(bundle: Sha256, planHash: MergeRow.PlanHash): Job.Id

  /** How the run ended; a row a restart already failed is left as it is. */
  def finish(id: Job.Id, outcome: Either[Problem, MergeApplyResult]): Unit

  /** One merge apply as the API answers it; `None` for an unknown id (or another kind of job). */
  def status(id: Job.Id): Option[MergeApplyJob]

object MergeApplyRuns:

  def apply(ds: DataSource): MergeApplyRuns = Implementation(ds)

  final private class Implementation(ds: DataSource) extends MergeApplyRuns:
    def running: Boolean = Transactor(ds).read(JobRepo.mergeApplyRunning)
    def start(bundle: Sha256, planHash: MergeRow.PlanHash): Job.Id =
      Transactor(ds).transact(JobRepo.startMergeApply(MergeRepo.BundleName(bundle.value), planHash))
    def finish(id: Job.Id, outcome: Either[Problem, MergeApplyResult]): Unit =
      Transactor(ds).transact(JobRepo.finishMergeApply(id, outcome.left.map(f => Problem(f.value))))
    def status(id: Job.Id): Option[MergeApplyJob] = Transactor(ds).read(JobRepo.mergeApply(id))

  /** One run as the in-memory twin keeps it. */
  final case class Run(bundle: Sha256, planHash: MergeRow.PlanHash, outcome: Option[Either[Problem, MergeApplyResult]])

  /** The rows in memory, keyed by id; ids are numbered in start order. */
  final class InMemory(state: AtomicReference[Map[Job.Id, Run]] = AtomicReference(Map.empty[Job.Id, Run]))
      extends MergeApplyRuns:
    private def idOf(n: Int): Job.Id = Job.Id.assume(UUID(0L, n.toLong).toString)

    def running: Boolean = state.get().values.exists(_.outcome.isEmpty)

    def start(bundle: Sha256, planHash: MergeRow.PlanHash): Job.Id =
      val next = state.updateAndGet(m => m + (idOf(m.size + 1) -> Run(bundle, planHash, None)))
      idOf(next.size)

    def finish(id: Job.Id, outcome: Either[Problem, MergeApplyResult]): Unit =
      val _ = state.updateAndGet(m =>
        m.get(id).filter(_.outcome.isEmpty).fold(m)(r => m + (id -> r.modify(_.outcome).setTo(Some(outcome))))
      )

    def status(id: Job.Id): Option[MergeApplyJob] =
      state.get().get(id).map { r =>
        val job = MergeApplyJob.Job(id.value)
        val bundle = Bundle.Name(r.bundle.value)
        val plan = MergePlan.PlanHash(r.planHash.value)
        r.outcome match
          case None           => MergeApplyJob(job, MergeJobState.Running, bundle, plan, MergeApplyJob.ElapsedMs(0L))
          case Some(Right(a)) =>
            MergeApplyJob.of(job, JobState.Done, bundle, plan, MergeApplyJob.ElapsedMs(0L), None, Some(a))
          case Some(Left(e)) =>
            MergeApplyJob.of(
              job,
              JobState.Failed,
              bundle,
              plan,
              MergeApplyJob.ElapsedMs(0L),
              Some(MergeApplyJob.Failure.Other(Problem(e.value))),
              None
            )
      }
