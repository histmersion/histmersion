package com.histmersion
package util.jobs
import com.histmersion.util.problem.Problems

import com.histmersion.util.database.Transactor

import com.histmersion.api.jobs.{JobInfo, JobState}

import hearth.kindlings.optics.syntax.*

import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import com.histmersion.domain.library.Citekey
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.automation.Queue

/** The rows of the jobs that run as one piece (an OCR, a re-OCR): created `queued`, moved from state to state by
  * compare-and-set, annotated with their progress. The jobs area owns the `jobs` table; a domain that runs such jobs
  * records them here.
  */
trait JobRecords:
  /** A new `queued` job; `acquireItem` is the acquisition item it ticks off when it succeeds. */
  def create(
      kind: Job.Kind,
      citekey: Option[Citekey],
      sha256: Option[Sha256],
      acquireItem: Option[JobRow.AcquireItem]
  ): JobInfo

  def get(id: Job.Id): Option[JobInfo]

  /** Compare-and-set `from` → `to` under the state machine; the new state, or `None` when it was refused. `error`:
    * every problem the job ended with, each kept.
    */
  def transition(
      id: Job.Id,
      from: JobState,
      to: JobState,
      progress: Option[JobRow.Progress] = None,
      error: Option[Problems] = None
  ): Option[JobState]

  /** Free-text progress on a `running` job. */
  def progress(id: Job.Id, text: JobRow.Progress): Unit

object JobRecords:
  /** Over the database `ds` names at each call (a reload may swap it). */

  def apply(ds: () => DataSource): JobRecords = Implementation(ds)

  final private class Implementation(ds: () => DataSource) extends JobRecords:
    def create(
        kind: Job.Kind,
        citekey: Option[Citekey],
        sha256: Option[Sha256],
        acquireItem: Option[JobRow.AcquireItem]
    ): JobInfo =
      Transactor(ds()).transact(JobRepo.create(kind, citekey, sha256, acquireItem))
    def get(id: Job.Id): Option[JobInfo] = Transactor(ds()).read(JobRepo.get(id))
    def transition(
        id: Job.Id,
        from: JobState,
        to: JobState,
        progress: Option[JobRow.Progress],
        error: Option[Problems]
    ): Option[JobState] =
      Transactor(ds()).transact(JobRepo.transition(id, from, to, progress, error.map(_.toNonEmpty)))
    def progress(id: Job.Id, text: JobRow.Progress): Unit = Transactor(ds()).transact(JobRepo.progress(id, text))

  /** The jobs in memory, as the table holds them; `clock` stands in for the database's `now()`. */
  final class InMemory(state: AtomicReference[Map[JobInfo.Id, JobInfo]], clock: Clock) extends JobRecords:
    def create(
        kind: Job.Kind,
        citekey: Option[Citekey],
        sha256: Option[Sha256],
        acquireItem: Option[JobRow.AcquireItem]
    ): JobInfo =
      val now = clock.instant().toString
      val job = JobInfo(
        SpatialHierarchyResult.Job.Id(UUID.randomUUID().toString),
        JobKinds.Wire(kind.value),
        JobState.Queued.wire,
        citekey.map(c => com.histmersion.api.library.Citekey(c.value)),
        None,
        None,
        sha256.map(h => com.histmersion.api.Sha256(h.value)),
        JobInfo.CreatedAt(now),
        JobInfo.UpdatedAt(now),
        acquireItem.map(a => Queue.QueueItem.Id(a.value))
      )
      val _ = state.updateAndGet(_.updated(job.id, job))
      job
    def get(id: Job.Id): Option[JobInfo] = state.get.get(JobInfo.Id(id.value))
    def transition(
        id: Job.Id,
        from: JobState,
        to: JobState,
        progress: Option[JobRow.Progress],
        error: Option[Problems]
    ): Option[JobState] =
      JobState.next(from, to).flatMap { target =>
        val now = clock.instant().toString
        val key = JobInfo.Id(id.value)
        val before = state.getAndUpdate(jobs =>
          jobs.get(key).filter(_.status == from.wire) match
            case Some(j) =>
              jobs.updated(
                key,
                j.modify(_.status)
                  .setTo(target.wire)
                  .modify(_.progress)
                  .setTo(progress.map(p => JobInfo.Progress(p.value)))
                  .modify(_.problems)
                  .setTo(error.map(_.toNonEmpty))
                  .modify(_.updatedAt)
                  .setTo(JobInfo.UpdatedAt(now))
              )
            case None => jobs
        )
        Option.when(before.get(key).exists(_.status == from.wire))(target)
      }
    def progress(id: Job.Id, text: JobRow.Progress): Unit =
      val now = clock.instant().toString
      val key = JobInfo.Id(id.value)
      val _ = state.updateAndGet(jobs =>
        jobs.get(key).filter(_.status == JobState.Running.wire) match
          case Some(j) =>
            jobs.updated(
              key,
              j.modify(_.progress)
                .setTo(Some(JobInfo.Progress(text.value)))
                .modify(_.updatedAt)
                .setTo(JobInfo.UpdatedAt(now))
            )
          case None => jobs
      )
