package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** A call that did not answer. */
enum AiFailure:
  /** The provider failed (unreachable, an error answer): the run pauses, as for any provider error. */
  case Error(message: AiFailure.Error.Message)

  /** The plan behind the model reported its own usage limit: the host passes the model over until `untilEpochMs` (or
    * its cool-down) and tries the chain's next one.
    */
  case LimitReached(message: AiFailure.LimitReached.Message, untilEpochMs: Option[AiFailure.LimitReached.UntilEpochMs] = None)

object AiFailure:
  object Error:
    type Message = Message.T
    object Message extends Newtype[String, Pure]
  object LimitReached:
    type Message = Message.T
    object Message extends Newtype[String, Pure]
    type UntilEpochMs = UntilEpochMs.T
    object UntilEpochMs extends Newtype[Long, Pure]
