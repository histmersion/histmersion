package com.histmersion
package domain.research.workflows
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.domain.automation.approvals.{ApprovalEffects, SubjectEffect}
import com.histmersion.domain.research.articles.{ArticlesControllers, ArticleService}
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

/** What approving an article's inferred filter does (#8817): the filter recorded on the article, unless it records one
  * by now ([[InferFilterPipeline.applyProposal]]).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class ArticleFilterApproval(articles: => ArticlesControllers, store: => ArticleService) extends SubjectEffect:
  val kind: Approval.Kind = Approval.Kind.ArticleFilter

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def apply(a: ApprovalInfo)(using Creator.New): Either[Problem, String] =
    a.filter
      .toRight("the approval carries no readable filter")
      .flatMap(InferFilterPipeline.applyProposal(articles, store, _))
      .left
      .map((text => Problem.Unclassified(Legacy.Text(text))))
