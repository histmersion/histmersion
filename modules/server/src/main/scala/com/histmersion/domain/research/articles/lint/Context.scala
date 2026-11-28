package com.histmersion
package domain.research.articles.lint

import com.histmersion.api.text.WikiLinks
import io.github.iltotore.iron.constraint.any.Pure

final case class Context(
    article: Context.Article = Context.Article(false),
    resolves: Option[WikiLinks.Occurrence.Target => Boolean] = None,
    isCitekey: Option[WikiLinks.Occurrence.Target => Boolean] = None
)

object Context:
  type Article = Article.T
  object Article extends Newtype[Boolean, Pure]
