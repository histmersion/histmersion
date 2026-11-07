package com.histmersion.server.views

import com.histmersion.api.research.ResolvedArticle
import com.histmersion.api.views.ResolvedId
import com.histmersion.api.library.ResolvedSource
import com.histmersion.api.Api

/** `GET /api/resolve/{id}`: what an identifier names across the library and research — a stored source, else an
  * article, else nothing; never an error.
  */
final class ResolveView(
    source: com.histmersion.domain.library.Citekey => Option[ResolvedSource],
    article: com.histmersion.domain.research.Slug => Option[ResolvedArticle]
):
  def apply(id: ResolvedId.Id): Either[Api.Error, ResolvedId] =
    val src = com.histmersion.domain.library.Citekey.option(id.value).flatMap(source)
    val art =
      if src.isDefined then None else com.histmersion.domain.research.Slug.option(id.value).flatMap(article)
    Right(ResolvedId(id, src, art))
