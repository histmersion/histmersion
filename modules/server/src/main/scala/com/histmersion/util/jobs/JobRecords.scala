package com.histmersion.util.jobs

import com.histmersion.util.database.Transactor

import com.histmersion.api.jobs.{JobInfo, JobState}

import hearth.kindlings.optics.syntax.*

import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** The rows of the jobs that run as one piece (an OCR, a re-OCR): created `queued`, moved from state to state by
  * compare-and-set, annotated with their progress. The jobs area owns the `jobs` table; a domain that runs such jobs
  * records them here.
  */
trait JobRecords:
  /** A new `queued` job; `acquireItem` is the acquisition item it ticks off when it succeeds. */
  def create(kind: com.histmersion.util.jobs.Job.Kind, citekey: Option[com.histmersion.domain.library.Citekey], sha256: Option[com.histmersion.Sha256], acquireItem: Option[com.histmersion.util.jobs.JobRow.AcquireItem]): JobInfo

  def get(id: com.histmersion.util.jobs.Job.Id): Option[JobInfo]

  /** Compare-and-set `from` → `to` under the state machine; the new state, or `None` when it was refused. */
  def transition(
      id: com.histmersion.util.jobs.Job.Id,
      from: JobState,
      to: JobState,
      progress: Option[com.histmersion.util.jobs.JobRepo.ProgressText] = None,
      error: Option[com.histmersion.util.jobs.JobRow.Error] = None
  ): Option[JobState]

  /** Free-text progress on a `running` job. */
  def progress(id: com.histmersion.util.jobs.Job.Id, text: com.histmersion.util.jobs.JobRepo.ProgressText): Unit

object JobRecords:
  /** Over the database `ds` names at each call (a reload may swap it). */

  def apply(ds: () => DataSource): JobRecords = Implementation(ds)

  final private class Implementation(ds: () => DataSource) extends JobRecords:
    def create(kind: com.histmersion.util.jobs.Job.Kind, citekey: Option[com.histmersion.domain.library.Citekey], sha256: Option[com.histmersion.Sha256], acquireItem: Option[com.histmersion.util.jobs.JobRow.AcquireItem]): JobInfo =
      Transactor(ds()).transact(JobRepo.create(kind, citekey, sha256, acquireItem))
    def get(id: com.histmersion.util.jobs.Job.Id): Option[JobInfo] = Transactor(ds()).read(JobRepo.get(id))
    def transition(
        id: com.histmersion.util.jobs.Job.Id,
        from: JobState,
        to: JobState,
        progress: Option[com.histmersion.util.jobs.JobRepo.ProgressText],
        error: Option[com.histmersion.util.jobs.JobRow.Error]
    ): Option[JobState] = Transactor(ds()).transact(JobRepo.transition(id, from, to, progress, error))
    def progress(id: com.histmersion.util.jobs.Job.Id, text: com.histmersion.util.jobs.JobRepo.ProgressText): Unit = Transactor(ds()).transact(JobRepo.progress(id, text))

  object Implementation:

  /** The jobs in memory, as the table holds them; `clock` stands in for the database's `now()`. */
  final class InMemory(state: AtomicReference[Map[InMemory.StateKey, JobInfo]], clock: Clock) extends JobRecords:
    def create(kind: com.histmersion.util.jobs.Job.Kind, citekey: Option[com.histmersion.domain.library.Citekey], sha256: Option[com.histmersion.Sha256], acquireItem: Option[com.histmersion.util.jobs.JobRow.AcquireItem]): JobInfo =
      val now = clock.instant().toString
      val job = JobInfo(
        com.histmersion.api.SpatialHierarchyResult.Job.Id(UUID.randomUUID().toString),
        com.histmersion.api.jobs.JobKinds.Wire(kind),
        JobState.Queued.wire,
        (citekey).map(com.histmersion.api.library.Citekey.apply),
        None,
        None,
        (sha256).map(com.histmersion.api.Sha256.apply),
        now,
        now,
        acquireItem
      )
      val _ = state.updateAndGet(_.updated(job.id, job))
      job
    def get(id: com.histmersion.util.jobs.Job.Id): Option[JobInfo] = state.get.get(id)
    def transition(
        id: com.histmersion.util.jobs.Job.Id,
        from: JobState,
        to: JobState,
        progress: Option[com.histmersion.util.jobs.JobRepo.ProgressText],
        error: Option[com.histmersion.util.jobs.JobRow.Error]
    ): Option[JobState] =
      JobState.next(from, to).flatMap { target =>
        val now = clock.instant().toString
        val before = state.getAndUpdate(jobs =>
          jobs.get(id).filter(_.status == from.wire) match
            case Some(j) =>
              jobs.updated(
                id,
                j.modify(_.status)
                  .setTo(target.wire)
                  .modify(_.progress)
                  .setTo(progress)
                  .modify(_.error)
                  .setTo(error)
                  .modify(_.updatedAt)
                  .setTo(now)
              )
            case None => jobs
        )
        Option.when(before.get(id).exists(_.status == from.wire))(target)
      }
    def progress(id: com.histmersion.util.jobs.Job.Id, text: com.histmersion.util.jobs.JobRepo.ProgressText): Unit =
      val now = clock.instant().toString
      val _ = state.updateAndGet(jobs =>
        jobs.get(id).filter(_.status == JobState.Running.wire) match
          case Some(j) => jobs.updated(id, j.modify(_.progress).setTo(Some(text)).modify(_.updatedAt).setTo(now))
          case None    => jobs
      )

  object InMemory:
    type StateKey = StateKey.T
    object StateKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

