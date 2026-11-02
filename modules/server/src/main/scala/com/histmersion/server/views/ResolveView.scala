package com.histmersion.server.views

import com.histmersion.api.{ApiError, ResolvedArticle, ResolvedId, ResolvedSource}

/** `GET /api/resolve/{id}`: what an identifier names across the library and research — a stored source, else an
  * article, else nothing; never an error.
  */
final class ResolveView(source: String => Option[ResolvedSource], article: String => Option[ResolvedArticle]):
  def apply(id: String): Either[ApiError, ResolvedId] =
    val src = source(id)
    val art = if src.isDefined then None else article(id)
    Right(ResolvedId(id, src, art))
