package com.histmersion.plugin

/** A call that did not answer. */
enum AiFailure:
  /** The provider failed (unreachable, an error answer): the run pauses, as for any provider error. */
  case Error(message: String)

  /** The plan behind the model reported its own usage limit: the host passes the model over until `untilEpochMs` (or
    * its cool-down) and tries the chain's next one.
    */
  case LimitReached(message: String, untilEpochMs: Option[Long] = None)
