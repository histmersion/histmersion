package com.histmersion.upstreams.core
enum CoreFailure:
  /** Not asked, or refused for its rate: the budget or CORE itself said to wait. */
  case Skipped(why: String)

  /** Asked, and it failed (unreachable, an error answer, unparseable JSON). */
  case Failed(why: String)

  def message: String = this match
    case Skipped(why) => s"CORE not asked: $why"
    case Failed(why)  => why
