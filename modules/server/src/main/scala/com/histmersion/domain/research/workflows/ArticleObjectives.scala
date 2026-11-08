package com.histmersion
package domain.research.workflows

import com.histmersion.api.automation.Managers.ReviewId
import com.histmersion.domain.automation.goals.ObjectiveProgress
import com.histmersion.domain.research.articles.ArticleService
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.research.Slug

/** Research's measure of a goal's progress: the articles linked to the goal that exist, finished once their review
  * ledger holds no open MAJOR and the review manager has no item open on them.
  */
final class ArticleObjectives(articles: => ArticleService, ledgers: => ReviewLedgerService) extends ObjectiveProgress:
  def linked(): Map[Goal.Slug, List[Slug]] = articles.articlesByGoal
  def finished(): Set[Slug] = ledgers.passedReview(ReviewLedgerService.ReviewId(ReviewId))

