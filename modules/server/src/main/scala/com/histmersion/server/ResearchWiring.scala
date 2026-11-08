package com.histmersion
package server

import com.histmersion.api.catalogue.{ArticleEventLead, Period}
import com.histmersion.domain.research.articles.{
  ArticlesControllers,
  ArticleSearch,
  ArticleService,
  CoverageService,
  PageBuilder
}
import com.histmersion.domain.research.articles.review.ReviewStateService
import com.histmersion.domain.research.workflows.ReviewLedgerService
import com.histmersion.api.Api
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.domain.research.Article

/** Research's services and controllers, built over one server's `deps`: the sources, their filters and citations are
  * the library's, an article's event leads the catalogue's; its note as the mirror renders it is handed in.
  */
final class ResearchWiring(
    deps: Deps,
    library: LibraryWiring,
    articleLeads: (ArticleEventLead.Body, List[Citekey], Option[Period]) => List[ArticleEventLead],
    note: Slug => Either[Api.Error, Article.Note]
):

  /** The articles as research stores them. */
  val articleStore: ArticleService = ArticleService(deps.transactor)

  /** The articles' review ledgers. */
  val ledgers: ReviewLedgerService = ReviewLedgerService(deps.transactor)

  val articles: ArticlesControllers = ArticlesControllers(
    articleStore,
    ArticleSearch(deps.transactor),
    CoverageService(deps.transactor),
    ReviewStateService(deps.transactor),
    library.sources,
    library.sourceStore,
    library.filters,
    ledgers.get,
    articleLeads,
    note,
    (k, id) => Deps.aliasTables.current(deps.ds, k, id),
    () => deps.cfg,
    deps.maintenance
  )

  /** The `/source/{citekey}` and `/article/{slug}` pages. */
  val pages: PageBuilder = PageBuilder(library.sources, library.sourceStore, library.citations, articles)
