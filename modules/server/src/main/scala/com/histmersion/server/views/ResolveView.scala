package com.histmersion
package server.views

import com.histmersion.api.research.ResolvedArticle
import com.histmersion.api.views.ResolvedId
import com.histmersion.api.library.ResolvedSource
import com.histmersion.api.Api
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug

/** `GET /api/resolve/{id}`: what an identifier names across the library and research — a stored source, else an
  * article, else nothing; never an error.
  */
final class ResolveView(
    source: Citekey => Option[ResolvedSource],
    article: Slug => Option[ResolvedArticle]
):
  def apply(id: ResolvedId.Id): Either[Api.Error, ResolvedId] =
    val src = Citekey.option(id.value).flatMap(source)
    val art =
      if src.isDefined then None else Slug.option(id.value).flatMap(article)
    Right(ResolvedId(id, src, art))
