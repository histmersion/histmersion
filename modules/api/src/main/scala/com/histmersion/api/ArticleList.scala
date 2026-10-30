package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/articles` — `total` ignores limit/offset. */
final case class ArticleList(
    total: Int,
    articles: List[ArticleSummary],
    /** Where this page starts, and the offset of the next one (absent on the last) — [[OffsetPage]]. */
    offset: Int = 0,
    next: Option[Int] = None,
    /** How many of the `total` are of each kind, the largest first (ignores limit/offset). */
    kinds: List[ArticleKindCount] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def page: OffsetPage = OffsetPage(total, offset, articles.size)
object ArticleList:
  given Schema[ArticleList] = summon[KindlingsSchema[ArticleList]].schema
  def of(total: Int, rows: List[ArticleSummary], offset: Int, kinds: List[ArticleKindCount] = Nil): ArticleList =
    ArticleList(total, rows, offset, OffsetPage(total, offset, rows.size).next, kinds)

  /** Per-kind counts as a list shows them: the largest first, then by kind. */
  def byKind(counts: List[ArticleKindCount]): List[ArticleKindCount] =
    counts.filter(_.count > 0).sortBy(k => (-k.count, k.kind))
