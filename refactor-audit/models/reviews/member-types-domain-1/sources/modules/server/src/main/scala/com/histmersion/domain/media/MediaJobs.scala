package com.histmersion.domain.media

import com.histmersion.api.jobs.JobInfo
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.{BackgroundJob, JobStops, WorkCarrier}

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import com.histmersion.api.Api

/** Media's runs — an import, the sources' pictures, as one request or as a job — one at a time: the polite pace is per
  * run, and two runs would ask twice as fast. A job is a `jobs` row a person may pause, resume or cancel between its
  * batches; a write takes the maintenance guard.
  */
final class MediaJobs(ds: DataSource, maintenance: WriteGate, pins: WorkCarrier, stops: JobStops):

  /** What runs now, when something does. */
  private val busy = AtomicReference[Option[String]](None)

  /** Media jobs share the one-request runs' mark: one media run of any sort at a time. */
  private val jobSlot = BackgroundJob.Slot(busy)

  /** `body` run now as `what`, unless another media run holds the slot. */
  def exclusive[A](what: com.histmersion.domain.media.MediaJobs.ExclusiveWhat)(body: => Either[Api.Error, A]): Either[Api.Error, A] =
    if busy.compareAndSet(None, Some(what)) then
      try body
      finally busy.set(None)
    else
      Left(
        Api.Error(
          s"a media run is already going (${busy.get.getOrElse("?")}) — see `histmersion jobs`; try again when it ends"
        )
      )

  /** `work` behind the maintenance guard (`what` names the write). */
  def guarded[A](what: com.histmersion.domain.media.MediaJobs.GuardedWhat)(work: => Either[Api.Error, A]): Either[Api.Error, A] = maintenance.guard(what)(work)

  /** Whether a maintenance run holds the lock: a job stops between its batches. */
  def held: com.histmersion.domain.media.MediaJobs.HeldResult = maintenance.active

  /** Start `body` as a `jobs` row of `kind` ([[BackgroundJob]]): `histmersion jobs <id>` follows it, a restart fails it
    * as `interrupted` like every other unfinished job, and a person may pause, resume or cancel it between its batches.
    * `body` returns the summary the finished job shows, or why it failed.
    */
  def start(kind: com.histmersion.domain.media.MediaJobs.StartKind, describe: com.histmersion.domain.media.MediaJobs.StartDescribe)(
      body: (com.histmersion.domain.media.MediaJobs.StartBodyArg1Arg1 => Unit) => Either[com.histmersion.domain.media.MediaJobs.StartBodyResultPart1, com.histmersion.domain.media.MediaJobs.StartBodyResultPart2]
  ): Either[Api.Error, JobInfo] =
    maintenance.guard(kind) {
      BackgroundJob.start(
        ds,
        kind,
        Some(com.histmersion.api.media.ImportJobKinds.Media(Some(describe))),
        jobSlot,
        "a media run",
        pins = pins,
        stops = Some(stops)
      )((_, progress) => BackgroundJob.Outcome.of(body(progress)))
    }

object MediaJobs:
  type ExclusiveWhat = ExclusiveWhat.T
  object ExclusiveWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardedWhat = GuardedWhat.T
  object GuardedWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeldResult = HeldResult.T
  object HeldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartKind = StartKind.T
  object StartKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartDescribe = StartDescribe.T
  object StartDescribe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBodyArg1Arg1 = StartBodyArg1Arg1.T
  object StartBodyArg1Arg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBodyResultPart1 = StartBodyResultPart1.T
  object StartBodyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBodyResultPart2 = StartBodyResultPart2.T
  object StartBodyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
