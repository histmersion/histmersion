package com.histmersion
package domain.research

import com.histmersion.domain.research.articles.ArticleService
import com.histmersion.domain.research.workflows.ReviewLedgerService

/** Research's services of one test, sharing their records. */
trait ResearchStores:
  def articles: ArticleService
  def ledgers: ReviewLedgerService
