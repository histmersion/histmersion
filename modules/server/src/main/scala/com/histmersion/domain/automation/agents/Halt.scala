package com.histmersion
package domain.automation.agents
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.jobs.JobState
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** Why a run stopped before its pipeline finished. A value, threaded through the pipeline's for-comprehension: a step
  * either produces its output or halts the run.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
enum Halt:
  /** `resumeAt`: when the run may go on by itself (an allowance window freeing); None = a person resumes it. */
  case Pause(reason: PauseReason, detail: Problem, resumeAt: Option[java.time.Instant] = None)
  case Cancel
  case Fail(message: Problem)

  /** The run waits for a person to decide approval `id` (a blocking step); it holds no worker, and goes back to the
    * queue when the approval is decided.
    */
  case Wait(id: Halt.Wait.Id, what: Halt.Wait.What)

object Halt:

  /** A run that fails with `problems`: one problem as it is, several as one [[Problem.Run.Failed]] holding each. */
  def failed(problems: Problems): Halt = Fail(problems.fold(identity)(Problem.Run.Failed(_)))
  type WaitId = WaitId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object WaitId extends Newtype[Long, Pure]
  object Wait:
    export Halt.WaitId as Id
    type What = What.T
    object What extends Newtype[String, Pure]
  export com.histmersion.api.automation.Stopped
