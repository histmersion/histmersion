package com.histmersion.server.agents

import com.histmersion.api.Managers.ReviewId
import com.histmersion.domain.automation.goals.ObjectiveProgress
import com.histmersion.server.Handlers

/** Research's measure of a goal's progress, registered by this transition adapter until research contributes it: the
  * articles linked to the goal that exist, finished once their review ledger holds no open MAJOR and the review manager
  * has no item open on them.
  */
final class ArticleObjectives(h: Handlers) extends ObjectiveProgress:
  def linked(): Map[String, List[String]] = h.articlesByGoal()
  def finished(): Set[String] = h.passedReview(ReviewId)
