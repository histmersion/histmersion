package com.histmersion.server

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

/** Research's services and controllers, built over one server's `deps`: the sources, their filters and citations are
  * the library's, an article's event leads the catalogue's; its note as the mirror renders it is handed in.
  */
final class ResearchWiring(
    deps: Deps,
    library: LibraryWiring,
    articleLeads: (ResearchWiring.ArticleLeadsArg1, List[ResearchWiring.ArticleLeadsArg2], Option[Period]) => List[ArticleEventLead],
    note: ResearchWiring.NoteArg1 => Either[Api.Error, ResearchWiring.NoteResultPart2]
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

object ResearchWiring:
  type ArticleLeadsArg1 = ArticleLeadsArg1.T
  object ArticleLeadsArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleLeadsArg2 = ArticleLeadsArg2.T
  object ArticleLeadsArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteArg1 = NoteArg1.T
  object NoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteResultPart2 = NoteResultPart2.T
  object NoteResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
