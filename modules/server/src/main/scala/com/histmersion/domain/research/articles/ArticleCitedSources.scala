package com.histmersion.domain.research.articles

/** The library's new-source search asks research which stored sources an article cites: research's own links. */
final class ArticleCitedSources(articles: ArticleService)
    extends com.histmersion.domain.library.acquisition.ArticleCitations:
  def cited(slug: com.histmersion.domain.research.articles.ArticleCitedSources.CitedSlug): List[com.histmersion.domain.research.articles.ArticleCitedSources.CitedResult] =
    articles.sourcesCited(slug).filter(_.title.isDefined).map(_.citekey)

object ArticleCitedSources:
  type CitedResult = CitedResult.T
  object CitedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedSlug = CitedSlug.T
  object CitedSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
