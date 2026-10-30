package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class ArticleList(
    total: Int,
    articles: List[ArticleSummary],
    offset: Int = 0,
    next: Option[Int] = None,
    kinds: List[ArticleKindCount] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def page: OffsetPage = OffsetPage(total, offset, articles.size)
object ArticleList:
  given Schema[ArticleList] = summon[KindlingsSchema[ArticleList]].schema
  def of(total: Int, rows: List[ArticleSummary], offset: Int, kinds: List[ArticleKindCount] = Nil): ArticleList =
    ArticleList(total, rows, offset, OffsetPage(total, offset, rows.size).next, kinds)

  def byKind(counts: List[ArticleKindCount]): List[ArticleKindCount] =
    counts.filter(_.count > 0).sortBy(k => (-k.count, k.kind))
