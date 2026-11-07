package com.histmersion.domain.research.articles

/** The library's new-source search asks research which stored sources an article cites: research's own links. */
final class ArticleCitedSources(articles: ArticleService)
    extends com.histmersion.domain.library.acquisition.ArticleCitations:
  def cited(slug: com.histmersion.domain.research.Slug): List[com.histmersion.domain.library.Citekey] =
    articles.sourcesCited(slug).filter(_.title.isDefined).map(c => com.histmersion.domain.library.Citekey.assume(c.citekey.value))
