package com.histmersion
package upstreams.core
import com.histmersion.util.problem.Problem

import io.github.iltotore.iron.constraint.any.Pure
enum CoreFailure derives CanEqual:
  /** Not asked, or refused for its rate: the budget or CORE itself said to wait. */
  case Skipped(why: Problem)

  /** Asked, and it failed (unreachable, an error answer, unparseable JSON). */
  case Failed(why: Problem)

  def message: Problem = this match
    case Skipped(why) => why
    case Failed(why)  => why
