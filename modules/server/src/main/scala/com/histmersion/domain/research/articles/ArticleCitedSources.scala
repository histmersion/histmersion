package com.histmersion.domain.research.articles

import com.histmersion.db.ArticleRepo

import javax.sql.DataSource

/** The library's new-source search asks research which stored sources an article cites: research's own links. */
final class ArticleCitedSources(ds: () => DataSource)
    extends com.histmersion.domain.library.acquisition.ArticleCitations:
  def cited(slug: String): List[String] =
    ArticleRepo.sourcesCited(ds(), slug).filter(_.title.isDefined).map(_.citekey)
