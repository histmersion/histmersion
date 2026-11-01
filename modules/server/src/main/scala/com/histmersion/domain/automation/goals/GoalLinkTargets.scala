package com.histmersion.domain.automation.goals

import com.histmersion.values.GoalLinkKind

/** What a goal's links point at, as other domains know it: the title of a linked source, article or queue item
  * (`None` when it no longer exists; a query has none) and a queue item's list. The database joins them; an in-memory
  * goal store is given them.
  */
trait GoalLinkTargets:
  def title(kind: GoalLinkKind, ref: String): Option[String]
  def queueList(ref: String): Option[String]

object GoalLinkTargets:
  /** Nothing exists anywhere. */
  val none: GoalLinkTargets = new GoalLinkTargets:
    def title(kind: GoalLinkKind, ref: String): Option[String] = None
    def queueList(ref: String): Option[String] = None
