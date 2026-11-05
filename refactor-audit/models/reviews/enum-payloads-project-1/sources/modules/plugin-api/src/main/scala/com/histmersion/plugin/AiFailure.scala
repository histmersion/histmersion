package com.histmersion.plugin

/** A call that did not answer. */
enum AiFailure:
  /** The provider failed (unreachable, an error answer): the run pauses, as for any provider error. */
  case Error(message: Error.Message)

  /** The plan behind the model reported its own usage limit: the host passes the model over until `untilEpochMs` (or
    * its cool-down) and tries the chain's next one.
    */
  case LimitReached(message: LimitReached.Message, untilEpochMs: Option[LimitReached.UntilEpochMs] = None)

object AiFailure:
  object Error:
    type Message = Message.T
    object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object LimitReached:
    type Message = Message.T
    object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UntilEpochMs = UntilEpochMs.T
    object UntilEpochMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
