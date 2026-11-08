package com.histmersion.upstreams.core
enum CoreFailure:
  /** Not asked, or refused for its rate: the budget or CORE itself said to wait. */
  case Skipped(why: CoreFailure.Skipped.Why)

  /** Asked, and it failed (unreachable, an error answer, unparseable JSON). */
  case Failed(why: CoreFailure.Failed.Why)

  def message: com.histmersion.upstreams.core.CoreFailure.Problem = this match
    case Skipped(why) => _root_.com.histmersion.upstreams.core.CoreFailure.Problem(s"CORE not asked: $why")
    case Failed(why)  => why

object CoreFailure:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Skipped:
    export com.histmersion.upstreams.core.CoreFailure.{Problem as Why}

  object Failed:
    export com.histmersion.upstreams.core.CoreFailure.{Problem as Why}

