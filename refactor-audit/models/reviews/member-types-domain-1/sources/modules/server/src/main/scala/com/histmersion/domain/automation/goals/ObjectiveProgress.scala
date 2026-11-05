package com.histmersion.domain.automation.goals

/** How a domain measures progress towards the goals (the engine's objectives) in its own terms: the items it links to
  * each goal that exist, and which of them count as finished — research's articles, finished once they passed their
  * review. The autopilot moves a goal's stage, the scheduler and the focus rank goals, from these.
  */
trait ObjectiveProgress:
  /** Every goal's linked items that exist, by goal slug, each list in order. */
  def linked(): Map[com.histmersion.domain.automation.goals.ObjectiveProgress.LinkedResultPart1, List[com.histmersion.domain.automation.goals.ObjectiveProgress.LinkedResultPart2]]

  /** The linked items that count as finished. */
  def finished(): Set[com.histmersion.domain.automation.goals.ObjectiveProgress.FinishedResult]

object ObjectiveProgress:
  type LinkedResultPart1 = LinkedResultPart1.T
  object LinkedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedResultPart2 = LinkedResultPart2.T
  object LinkedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinishedResult = FinishedResult.T
  object FinishedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
