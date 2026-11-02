package com.histmersion.server.views

import com.histmersion.api.research.ResolvedArticle
import com.histmersion.api.views.ResolvedId
import com.histmersion.api.library.ResolvedSource
import com.histmersion.api.Api

/** `GET /api/resolve/{id}`: what an identifier names across the library and research — a stored source, else an
  * article, else nothing; never an error.
  */
final class ResolveView(source: String => Option[ResolvedSource], article: String => Option[ResolvedArticle]):
  def apply(id: String): Either[Api.Error, ResolvedId] =
    val src = source(id)
    val art = if src.isDefined then None else article(id)
    Right(ResolvedId(id, src, art))
