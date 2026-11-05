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
  def request(id: String, stop: JobStop): Unit = { val _ = requested.put(id, stop) }

  /** The stop asked of job `id`, if one was. */
  def requestedFor(id: String): Option[JobStop] = Option(requested.get(id))

  /** Job `id` stopped (or ended): its request is answered. */
  def answered(id: String): Unit = { val _ = requested.remove(id) }

  /** How paused job `id` goes on: runs its remaining parts on the same job row. */
  def pausedWith(id: String, resume: () => Either[String, Unit]): Unit = { val _ = resumers.put(id, resume) }

  /** Whether this server can resume paused job `id`. */
  def canResume(id: String): Boolean = resumers.containsKey(id)

  /** Resume paused job `id` (its way to go on is used once). */
  def resume(id: String): Either[String, Unit] =
    Option(resumers.remove(id)).toRight(s"job $id cannot be resumed by this server").flatMap(_())

  /** Paused job `id` was cancelled: it will not go on. */
  def forget(id: String): Unit =
    val _ = resumers.remove(id)
    answered(id)
