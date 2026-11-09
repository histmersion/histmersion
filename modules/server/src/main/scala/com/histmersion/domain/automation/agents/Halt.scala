package com.histmersion
package domain.automation.agents

import com.histmersion.domain.Problem
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.jobs.JobState
import com.histmersion.api.Problem as ApiProblem
import com.histmersion.api.VocabularyCodecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** Why a run stopped before its pipeline finished. A value, threaded through the pipeline's for-comprehension: a step
  * either produces its output or halts the run.
  */
enum Halt derives CanEqual:
  /** `resumeAt`: when the run may go on by itself (an allowance window freeing); None = a person resumes it. */
  case Pause(reason: PauseReason, detail: Problem, resumeAt: Option[java.time.Instant] = None)
  case Cancel
  case Fail(message: Problem)

  /** The run waits for a person to decide approval `id` (a blocking step); it holds no worker, and goes back to the
    * queue when the approval is decided.
    */
  case Wait(id: Halt.Wait.Id, what: Halt.Wait.What)

object Halt:
  type WaitId = WaitId.T
  object WaitId extends Newtype[Long, Pure]
  object Wait:
    type Id = WaitId
    val Id: WaitId.type = WaitId
    type What = What.T
    object What extends Newtype[String, Pure]

  /** How a job stopped, as the sentence the `jobs.error` column keeps it. The sentences code writes are the
    * [[Stopped.Cause]]s; [[Stopped.cause]] reads any stored sentence back (what no writer produces is
    * [[Stopped.Cause.Failed]]) and [[Stopped.of]] writes a cause as the same sentence, so a row written by an earlier
    * version reads as it was and one written now reads in an earlier version.
    */
  type Stopped = Stopped.T
  object Stopped extends Newtype[String, Pure]:

    enum Cause derives CanEqual:

      /** A run that stopped for `reason` and waits for a person, an allowance or a clock: stored `<reason>: <detail>`. */
      case Paused(reason: PauseReason, detail: ApiProblem)

      /** A job a restart cut short: stored `interrupted`. */
      case Interrupted

      /** A job the autopilot's drain stopped; a later pass takes it up. */
      case Draining

      /** Any other sentence, as written (a failure's message, `cancelled`). */
      case Failed(text: ApiProblem)

      /** Is this a pause for `reason`? */
      def pausedOn(reason: PauseReason): Boolean = this match
        case Paused(r, _) => r == reason
        case _            => false

      /** Does a later job take up where this one stopped? Only one a restart or the drain stopped. */
      def resumable: Boolean = this match
        case Interrupted | Draining => true
        case _                      => false

    /** The sentence of a job the drain stopped. */
    val DrainingText = "stopped while the autopilot drains: it resumes once the drain is lifted"

    private def prefix(reason: PauseReason): String = s"${reason.wire}: "

    /** The sentence a cause is stored as. */
    def of(c: Cause): Stopped = c match
      case Cause.Paused(reason, detail) => Stopped(s"${prefix(reason)}$detail")
      case Cause.Interrupted            => Stopped(s"${JobState.Interrupted}")
      case Cause.Draining               => Stopped(s"${DrainingText}")
      case Cause.Failed(text)           => Stopped(s"$text")

    /** The stored sentence of a column read as a problem. */
    def from(stored: ApiProblem): Stopped = Stopped(s"$stored")

    /** The sentence of a cause, to store or show. */
    def write(c: Cause): ApiProblem = of(c).problem

    extension (s: Stopped)
      def problem: ApiProblem = ApiProblem(s"$s")

      /** What the sentence says. A sentence is only read as a pause, an interruption or a drain when writing that cause
        * gives the sentence back exactly, so [[of]] of [[cause]] is always the stored sentence.
        */
      def cause: Cause =
        val text: String = s.value
        val read: Cause = text match
          case JobState.Interrupted           => Cause.Interrupted
          case DrainingText => Cause.Draining
          case _                              =>
            PauseReason.values
              .find(r => text.startsWith(prefix(r)))
              .fold[Cause](Cause.Failed(ApiProblem(s"$s")))(r => Cause.Paused(r, ApiProblem(s"${text.drop(prefix(r).length)}")))
        if of(read).value == text then read else Cause.Failed(ApiProblem(s"$s"))
