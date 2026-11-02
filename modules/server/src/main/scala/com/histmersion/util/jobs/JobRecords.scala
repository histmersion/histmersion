package com.histmersion.util.jobs

import com.histmersion.api.{JobInfo, JobState}

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
  def create(kind: String, citekey: Option[String], sha256: Option[String], acquireItem: Option[Int]): JobInfo

  def get(id: String): Option[JobInfo]

  /** Compare-and-set `from` → `to` under the state machine; the new state, or `None` when it was refused. */
  def transition(
      id: String,
      from: JobState,
      to: JobState,
      progress: Option[String] = None,
      error: Option[String] = None
  ): Option[JobState]

  /** Free-text progress on a `running` job. */
  def progress(id: String, text: String): Unit

object JobRecords:
  /** Over the database `ds` names at each call (a reload may swap it). */
  def apply(ds: () => DataSource): JobRecords = Implementation(ds)

  final private class Implementation(ds: () => DataSource) extends JobRecords:
    def create(kind: String, citekey: Option[String], sha256: Option[String], acquireItem: Option[Int]): JobInfo =
      JobRepo.create(ds(), kind, citekey, sha256, acquireItem)
    def get(id: String): Option[JobInfo] = JobRepo.get(ds(), id)
    def transition(
        id: String,
        from: JobState,
        to: JobState,
        progress: Option[String],
        error: Option[String]
    ): Option[JobState] = JobRepo.transition(ds(), id, from, to, progress, error)
    def progress(id: String, text: String): Unit = JobRepo.progress(ds(), id, text)

  /** The jobs in memory, as the table holds them; `clock` stands in for the database's `now()`. */
  final class InMemory(state: AtomicReference[Map[String, JobInfo]], clock: Clock) extends JobRecords:
    def create(kind: String, citekey: Option[String], sha256: Option[String], acquireItem: Option[Int]): JobInfo =
      val now = clock.instant().toString
      val job = JobInfo(
        UUID.randomUUID().toString,
        kind,
        JobState.Queued.wire,
        citekey,
        None,
        None,
        sha256,
        now,
        now,
        acquireItem
      )
      val _ = state.updateAndGet(_.updated(job.id, job))
      job
    def get(id: String): Option[JobInfo] = state.get.get(id)
    def transition(
        id: String,
        from: JobState,
        to: JobState,
        progress: Option[String],
        error: Option[String]
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
    def progress(id: String, text: String): Unit =
      val now = clock.instant().toString
      val _ = state.updateAndGet(jobs =>
        jobs.get(id).filter(_.status == JobState.Running.wire) match
          case Some(j) => jobs.updated(id, j.modify(_.progress).setTo(Some(text)).modify(_.updatedAt).setTo(now))
          case None    => jobs
      )
