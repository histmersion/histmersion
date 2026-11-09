package com.histmersion
package domain.automation.goals

/** What a goal's links point at, as other domains know it: the title of a linked source, article or queue item (`None`
  * when it no longer exists; a query has none) and a queue item's list. The database joins them; an in-memory goal
  * store is given them.
  */
import com.histmersion.domain.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
trait GoalLinkTargets:
  def title(kind: Goal.LinkKind, ref: BGoalLinkRow.Ref): Option[GoalLinkTargets.LinkTitle]
  def queueList(ref: BGoalLinkRow.Ref): Option[GoalLinkTargets.QueueLines]

object GoalLinkTargets:
  /** Nothing exists anywhere. */
  type LinkTitle = LinkTitle.T
  object LinkTitle extends Newtype[String, Pure]
  type QueueLines = QueueLines.T
  object QueueLines extends Newtype[String, Pure]

  val none: GoalLinkTargets = new GoalLinkTargets:
    def title(kind: Goal.LinkKind, ref: BGoalLinkRow.Ref): Option[LinkTitle] = None
    def queueList(ref: BGoalLinkRow.Ref): Option[QueueLines] = None
