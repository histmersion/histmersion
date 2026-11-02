package com.histmersion.domain.research.workflows

import com.histmersion.api.Managers.ReviewId
import com.histmersion.domain.automation.goals.ObjectiveProgress
import com.histmersion.domain.research.articles.ArticleService

/** Research's measure of a goal's progress: the articles linked to the goal that exist, finished once their review
  * ledger holds no open MAJOR and the review manager has no item open on them.
  */
final class ArticleObjectives(articles: => ArticleService, ledgers: => ReviewLedgerService) extends ObjectiveProgress:
  def linked(): Map[String, List[String]] = articles.articlesByGoal
  def finished(): Set[String] = ledgers.passedReview(ReviewId)
