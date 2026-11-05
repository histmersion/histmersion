package com.histmersion.server.views

import com.histmersion.api.research.ResolvedArticle
import com.histmersion.api.views.ResolvedId
import com.histmersion.api.library.ResolvedSource
import com.histmersion.api.Api

/** `GET /api/resolve/{id}`: what an identifier names across the library and research — a stored source, else an
  * article, else nothing; never an error.
  */
final class ResolveView(source: ResolveView.SourceArg1 => Option[ResolvedSource], article: ResolveView.ArticleArg1 => Option[ResolvedArticle]):
  def apply(id: com.histmersion.server.views.ResolveView.ApplyId): Either[Api.Error, ResolvedId] =
    val src = source(id)
    val art = if src.isDefined then None else article(id)
    Right(ResolvedId(id, src, art))

object ResolveView:
  type SourceArg1 = SourceArg1.T
  object SourceArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleArg1 = ArticleArg1.T
  object ArticleArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ApplyId = ApplyId.T
  object ApplyId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
