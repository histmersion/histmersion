package com.histmersion.domain.research.workflows

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.domain.automation.approvals.SubjectEffect
import com.histmersion.domain.research.articles.{ArticlesControllers, ArticleService}
import com.histmersion.domain.automation.Approval

/** What approving an article's inferred filter does (#8817): the filter recorded on the article, unless it records one
  * by now ([[InferFilterPipeline.applyProposal]]).
  */
final class ArticleFilterApproval(articles: => ArticlesControllers, store: => ArticleService) extends SubjectEffect:
  val kind: Approval.Kind = Approval.Kind.ArticleFilter

  def apply(a: ApprovalInfo): Either[com.histmersion.domain.research.workflows.ArticleFilterApproval.Problem, com.histmersion.domain.automation.Approval.Outcome] =
    a.filter
      .toRight("the approval carries no readable filter")
      .flatMap(InferFilterPipeline.applyProposal(articles, store, _))

object ArticleFilterApproval:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
