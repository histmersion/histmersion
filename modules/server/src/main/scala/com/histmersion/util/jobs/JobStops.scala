package com.histmersion
package util.jobs
import com.histmersion.util.problem.{Legacy, Problem, Problems}

import java.util.concurrent.ConcurrentHashMap
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit

/** The person's requests to stop running background jobs, and how each paused job goes on. Held in memory by the server
  * for its lifetime: a stop is asked of a job running in this process, and a restart loses every paused job's way to go
  * on (the startup reconciliation fails those as interrupted).
  */
final class JobStops:
  private val requested = ConcurrentHashMap[Job.Id, JobStop]()
  private val resumers = ConcurrentHashMap[Job.Id, () => Either[Problems, Unit]]()

  /** Ask job `id` to stop at its next part boundary. */
  def request(id: Job.Id, stop: JobStop): Unit = { val _ = requested.put(id, stop) }

  /** The stop asked of job `id`, if one was. */
  def requestedFor(id: Job.Id): Option[JobStop] = Option(requested.get(id))

  /** Job `id` stopped (or ended): its request is answered. */
  def answered(id: Job.Id): Unit = { val _ = requested.remove(id) }

  /** How paused job `id` goes on: runs its remaining parts on the same job row. */
  def pausedWith(id: Job.Id, resume: () => Either[Problems, Unit]): Unit = { val _ = resumers.put(id, resume) }

  /** Whether this server can resume paused job `id`. */
  def canResume(id: Job.Id): Boolean = resumers.containsKey(id)

  /** Resume paused job `id` (its way to go on is used once). */
  def resume(id: Job.Id): Either[Problems, Unit] =
    Option(resumers.remove(id))
      .toRight(Problem.Unclassified(Legacy.Text(s"job $id cannot be resumed by this server")))
      .flatMap(_())

  /** Paused job `id` was cancelled: it will not go on. */
  def forget(id: Job.Id): Unit =
    val _ = resumers.remove(id)
    answered(id)

  /** The paused jobs' ways to go on, as a restore holds them: afterwards they name rows the restore replaced (the
    * restored database's unfinished jobs are failed), so each is forgotten, with every stop asked.
    */
  val quiescent: Quiescent = new Quiescent:
    val name: Quiescent.Name = Quiescent.Name("the paused background jobs")
    def quiesce(): Unit = ()
    def busy: Vector[Quiescent.Busy] = Vector.empty
    def restored()(using WritePermit): Unit =
      resumers.clear()
      requested.clear()
    def resume(): Unit = ()
