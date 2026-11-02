package com.histmersion.domain.research

import com.histmersion.domain.research.articles.{ArticleMergeRules, ArticlesCarried, ArticleVersionMergeRules}
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}

/** What research carries in a bundle and how a merge treats its records: the articles and what they own. */
object ResearchExchange:

  /** The articles, their versions and their old slugs. */
  val carried: List[CarriedKind] = ArticlesCarried.all

  /** How a merge treats articles and their versions. */
  val mergeRules: List[MergeRules] = List(ArticleMergeRules, ArticleVersionMergeRules)
