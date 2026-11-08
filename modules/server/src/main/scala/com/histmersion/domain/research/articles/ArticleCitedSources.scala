package com.histmersion
package domain.research.articles

import com.histmersion.domain.library.acquisition.ArticleCitations
import com.histmersion.domain.research.Slug
import com.histmersion.domain.library.Citekey

/** The library's new-source search asks research which stored sources an article cites: research's own links. */
final class ArticleCitedSources(articles: ArticleService)
    extends ArticleCitations:
  def cited(slug: Slug): List[Citekey] =
    articles.sourcesCited(slug).filter(_.title.isDefined).map(c => Citekey.assume(c.citekey.value))
