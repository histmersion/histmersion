package com.histmersion.domain.research

import com.histmersion.domain.research.articles.ArticleService

/** Research's services of one test, sharing their records. */
trait ResearchStores:
  def articles: ArticleService
