package com.histmersion.upstreams.core
enum CoreFailure:
  /** Not asked, or refused for its rate: the budget or CORE itself said to wait. */
  case Skipped(why: CoreFailure.Skipped.Why)

  /** Asked, and it failed (unreachable, an error answer, unparseable JSON). */
  case Failed(why: CoreFailure.Failed.Why)

  def message: com.histmersion.upstreams.core.CoreFailure.MessageResult = this match
    case Skipped(why) => s"CORE not asked: $why"
    case Failed(why)  => why

object CoreFailure:
  object Skipped:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Failed:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MessageResult = MessageResult.T
  object MessageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
