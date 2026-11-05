package com.histmersion.domain.automation.goals

/** What a goal's links point at, as other domains know it: the title of a linked source, article or queue item (`None`
  * when it no longer exists; a query has none) and a queue item's list. The database joins them; an in-memory goal
  * store is given them.
  */
import com.histmersion.domain.automation.Goal
trait GoalLinkTargets:
  def title(kind: Goal.LinkKind, ref: com.histmersion.domain.automation.goals.GoalLinkTargets.TitleRef): Option[com.histmersion.domain.automation.goals.GoalLinkTargets.TitleResult]
  def queueList(ref: com.histmersion.domain.automation.goals.GoalLinkTargets.QueueListRef): Option[com.histmersion.domain.automation.goals.GoalLinkTargets.QueueListResult]

object GoalLinkTargets:
  /** Nothing exists anywhere. */
  type TitleResult = TitleResult.T
  object TitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleRef = TitleRef.T
  object TitleRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueListResult = QueueListResult.T
  object QueueListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueListRef = QueueListRef.T
  object QueueListRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val none: GoalLinkTargets = new GoalLinkTargets:
    def title(kind: Goal.LinkKind, ref: com.histmersion.domain.automation.goals.GoalLinkTargets.TitleRef): Option[com.histmersion.domain.automation.goals.GoalLinkTargets.TitleResult] = None
    def queueList(ref: com.histmersion.domain.automation.goals.GoalLinkTargets.QueueListRef): Option[com.histmersion.domain.automation.goals.GoalLinkTargets.QueueListResult] = None
