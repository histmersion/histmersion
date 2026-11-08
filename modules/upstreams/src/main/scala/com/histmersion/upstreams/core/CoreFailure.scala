package com.histmersion
package upstreams.core

import io.github.iltotore.iron.constraint.any.Pure
enum CoreFailure derives CanEqual:
  /** Not asked, or refused for its rate: the budget or CORE itself said to wait. */
  case Skipped(why: CoreFailure.Skipped.Why)

  /** Asked, and it failed (unreachable, an error answer, unparseable JSON). */
  case Failed(why: CoreFailure.Failed.Why)

  def message: CoreFailure.Problem = this match
    case Skipped(why) => CoreFailure.Problem(s"CORE not asked: $why")
    case Failed(why)  => why

object CoreFailure:
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  object Skipped:
    export CoreFailure.{Problem as Why}

  object Failed:
    export CoreFailure.{Problem as Why}

