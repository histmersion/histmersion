package com.histmersion
package domain.research.workflows
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.domain.automation.approvals.{ApprovalEffects, SubjectEffect}
import com.histmersion.domain.research.articles.{ArticlesControllers, ArticleService}
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.Creator

/** What approving an article's inferred filter does (#8817): the filter recorded on the article, unless it records one
  * by now ([[InferFilterPipeline.applyProposal]]).
  */
final class ArticleFilterApproval(articles: => ArticlesControllers, store: => ArticleService) extends SubjectEffect:
  val kind: Approval.Kind = Approval.Kind.ArticleFilter

  def apply(a: ApprovalInfo)(using Creator.New): Either[Problem, String] =
    a.filter
      .toRight("the approval carries no readable filter")
      .flatMap(InferFilterPipeline.applyProposal(articles, store, _))
      .left
      .map((text => Problem.Unclassified(Legacy.Text(text))))
