package com.histmersion.util.jobs

import java.util.concurrent.ConcurrentHashMap

/** The person's requests to stop running background jobs, and how each paused job goes on. Held in memory by the server
  * for its lifetime: a stop is asked of a job running in this process, and a restart loses every paused job's way to go
  * on (the startup reconciliation fails those as interrupted).
  */
final class JobStops:
  private val requested = ConcurrentHashMap[String, JobStop]()
  private val resumers = ConcurrentHashMap[String, () => Either[String, Unit]]()

  /** Ask job `id` to stop at its next part boundary. */
  def request(id: com.histmersion.util.jobs.JobStops.RequestId, stop: JobStop): Unit = { val _ = requested.put(id, stop) }

  /** The stop asked of job `id`, if one was. */
  def requestedFor(id: com.histmersion.util.jobs.JobStops.RequestedForId): Option[JobStop] = Option(requested.get(id))

  /** Job `id` stopped (or ended): its request is answered. */
  def answered(id: com.histmersion.util.jobs.JobStops.AnsweredId): Unit = { val _ = requested.remove(id) }

  /** How paused job `id` goes on: runs its remaining parts on the same job row. */
  def pausedWith(id: com.histmersion.util.jobs.JobStops.PausedWithId, resume: () => Either[com.histmersion.util.jobs.JobStops.PausedWithResumeResultPart1, Unit]): Unit = { val _ = resumers.put(id, resume) }

  /** Whether this server can resume paused job `id`. */
  def canResume(id: com.histmersion.util.jobs.JobStops.CanResumeId): com.histmersion.util.jobs.JobStops.CanResumeResult = resumers.containsKey(id)

  /** Resume paused job `id` (its way to go on is used once). */
  def resume(id: com.histmersion.util.jobs.JobStops.ResumeId): Either[com.histmersion.util.jobs.JobStops.ResumeResultPart1, Unit] =
    Option(resumers.remove(id)).toRight(s"job $id cannot be resumed by this server").flatMap(_())

  /** Paused job `id` was cancelled: it will not go on. */
  def forget(id: com.histmersion.util.jobs.JobStops.ForgetId): Unit =
    val _ = resumers.remove(id)
    answered(id)

object JobStops:
  type RequestId = RequestId.T
  object RequestId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestedForId = RequestedForId.T
  object RequestedForId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnsweredId = AnsweredId.T
  object AnsweredId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PausedWithId = PausedWithId.T
  object PausedWithId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PausedWithResumeResultPart1 = PausedWithResumeResultPart1.T
  object PausedWithResumeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanResumeResult = CanResumeResult.T
  object CanResumeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanResumeId = CanResumeId.T
  object CanResumeId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumeResultPart1 = ResumeResultPart1.T
  object ResumeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumeId = ResumeId.T
  object ResumeId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ForgetId = ForgetId.T
  object ForgetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
