package com.histmersion
package plugin

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure

/** A call that did not answer. */
enum AiFailure derives CanEqual:
  /** The provider failed (unreachable, an error answer): the run pauses, as for any provider error. */
  case Error(message: Problem)

  /** The plan behind the model reported its own usage limit: the host passes the model over until `untilEpochMs` (or
    * its cool-down) and tries the chain's next one.
    */
  case LimitReached(
      message: Problem,
      untilEpochMs: Option[AiFailure.LimitReached.UntilEpochMs] = None
  )

object AiFailure:
  object LimitReached:
    type UntilEpochMs = UntilEpochMs.T
    object UntilEpochMs extends Newtype[Long, Pure]
