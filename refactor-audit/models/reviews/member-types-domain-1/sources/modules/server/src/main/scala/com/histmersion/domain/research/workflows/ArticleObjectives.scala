package com.histmersion.domain.research.workflows

import com.histmersion.api.automation.Managers.ReviewId
import com.histmersion.domain.automation.goals.ObjectiveProgress
import com.histmersion.domain.research.articles.ArticleService

/** Research's measure of a goal's progress: the articles linked to the goal that exist, finished once their review
  * ledger holds no open MAJOR and the review manager has no item open on them.
  */
final class ArticleObjectives(articles: => ArticleService, ledgers: => ReviewLedgerService) extends ObjectiveProgress:
  def linked(): Map[com.histmersion.domain.research.workflows.ArticleObjectives.LinkedResultPart1, List[com.histmersion.domain.research.workflows.ArticleObjectives.LinkedResultPart2]] = articles.articlesByGoal
  def finished(): Set[com.histmersion.domain.research.workflows.ArticleObjectives.FinishedResult] = ledgers.passedReview(ReviewId)

object ArticleObjectives:
  type LinkedResultPart1 = LinkedResultPart1.T
  object LinkedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedResultPart2 = LinkedResultPart2.T
  object LinkedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinishedResult = FinishedResult.T
  object FinishedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
