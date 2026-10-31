package com.histmersion.util.jobs

import com.histmersion.api.{JobState, MergeApplyJob, MergeApplyResult}

import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** The `jobs` rows of merge applies (kind `merge-apply`): one is `running` from its start (it runs on its own thread,
  * never queued), with its bundle and its plan; it ends `done` with its report or `failed` with why. A restart fails an
  * unfinished one as `interrupted`, like every other job.
  */
trait MergeApplyRuns:
  /** Is a merge apply running? One at a time. */
  def running: Boolean

  /** A new running row for the bundle and plan; its id. */
  def start(bundle: String, planHash: String): String

  /** How the run ended; a row a restart already failed is left as it is. */
  def finish(id: String, outcome: Either[String, MergeApplyResult]): Unit

  /** One merge apply as the API answers it; `None` for an unknown id (or another kind of job). */
  def status(id: String): Option[MergeApplyJob]

object MergeApplyRuns:
  def apply(ds: DataSource): MergeApplyRuns = Implementation(ds)

  final private class Implementation(ds: DataSource) extends MergeApplyRuns:
    def running: Boolean = JobRepo.mergeApplyRunning(ds)
    def start(bundle: String, planHash: String): String = JobRepo.startMergeApply(ds, bundle, planHash)
    def finish(id: String, outcome: Either[String, MergeApplyResult]): Unit = JobRepo.finishMergeApply(ds, id, outcome)
    def status(id: String): Option[MergeApplyJob] = JobRepo.mergeApply(ds, id)

  /** One run as the in-memory twin keeps it. */
  final case class Run(bundle: String, planHash: String, outcome: Option[Either[String, MergeApplyResult]])

  /** The rows in memory, keyed by id; ids are numbered in start order. */
  final class InMemory(state: AtomicReference[Map[String, Run]] = AtomicReference(Map.empty[String, Run]))
      extends MergeApplyRuns:
    def running: Boolean = state.get().values.exists(_.outcome.isEmpty)

    def start(bundle: String, planHash: String): String =
      val next = state.updateAndGet(m => m + (s"merge-apply-${m.size + 1}" -> Run(bundle, planHash, None)))
      s"merge-apply-${next.size}"

    def finish(id: String, outcome: Either[String, MergeApplyResult]): Unit =
      val _ = state.updateAndGet(m =>
        m.get(id).filter(_.outcome.isEmpty).fold(m)(r => m + (id -> r.modify(_.outcome).setTo(Some(outcome))))
      )

    def status(id: String): Option[MergeApplyJob] =
      state.get().get(id.trim).map { r =>
        r.outcome match
          case None => MergeApplyJob(id.trim, com.histmersion.api.MergeJobState.Running, r.bundle, r.planHash, 0)
          case Some(Right(a)) => MergeApplyJob.of(id.trim, JobState.Done, r.bundle, r.planHash, 0, None, Some(a))
          case Some(Left(e))  => MergeApplyJob.of(id.trim, JobState.Failed, r.bundle, r.planHash, 0, Some(e), None)
      }
