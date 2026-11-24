package com.histmersion
package domain.research.workflows

import com.histmersion.api.automation.Managers.ReviewId
import com.histmersion.domain.automation.goals.ObjectiveProgress
import com.histmersion.domain.research.articles.ArticleService
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.research.Slug
import com.histmersion.lint.{LintRule, tolerated}

/** Research's measure of a goal's progress: the articles linked to the goal that exist, finished once their review
  * ledger holds no open MAJOR and the review manager has no item open on them.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class ArticleObjectives(articles: => ArticleService, ledgers: => ReviewLedgerService) extends ObjectiveProgress:
  def linked(): Map[Goal.Slug, List[Slug]] = articles.articlesByGoal
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def finished(): Set[Slug] = ledgers.passedReview(ReviewLedgerService.ReviewId(ReviewId))
