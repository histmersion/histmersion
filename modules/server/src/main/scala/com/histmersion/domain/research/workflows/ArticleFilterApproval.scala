package com.histmersion.domain.research.workflows

import com.histmersion.api.Agents.ApprovalInfo
import com.histmersion.domain.automation.approvals.SubjectEffect
import com.histmersion.domain.research.articles.{ArticlesControllers, ArticleService}
import com.histmersion.values.ApprovalKind

/** What approving an article's inferred filter does (#8817): the filter recorded on the article, unless it records one
  * by now ([[InferFilterPipeline.applyProposal]]).
  */
final class ArticleFilterApproval(articles: => ArticlesControllers, store: => ArticleService) extends SubjectEffect:
  val kind: ApprovalKind = ApprovalKind.ArticleFilter

  def apply(a: ApprovalInfo): Either[String, String] =
    a.filter
      .toRight("the approval carries no readable filter")
      .flatMap(InferFilterPipeline.applyProposal(articles, store, _))
