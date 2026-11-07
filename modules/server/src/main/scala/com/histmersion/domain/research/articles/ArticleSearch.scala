package com.histmersion.domain.research.articles

import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.util.database.Transactor
import com.histmersion.api.research.Article

/** The articles related to a filter's sources, narrowed by kind and searched by their own text: a mechanism over the
  * library's filter SQL and the articles' full-text index, so it has no in-memory twin.
  */
trait ArticleSearch:
  def list(
      filter: Filter,
      scope: PlaceScope,
      kind: Option[com.histmersion.domain.research.Article.Kind],
      text: Option[com.histmersion.domain.research.articles.ArticleSearch.Query],
      limit: com.histmersion.domain.research.articles.ArticleSearch.ListLimit,
      offset: com.histmersion.Offset
  ): Article.List

object ArticleSearch:
  type Query = Query.T
  object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ListLimit = ListLimit.T
  object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): ArticleSearch = Implementation(db)

  final private class Implementation(db: Transactor) extends ArticleSearch:
    def list(
        filter: Filter,
        scope: PlaceScope,
        kind: Option[com.histmersion.domain.research.Article.Kind],
        text: Option[com.histmersion.domain.research.articles.ArticleSearch.Query],
        limit: com.histmersion.domain.research.articles.ArticleSearch.Implementation.ListLimit,
        offset: com.histmersion.Offset
    ): Article.List = db.read(ArticleRepo.list(filter, scope, kind, text, limit, offset))

  object Implementation:
    type ListLimit = ListLimit.T
    object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
