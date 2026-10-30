package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `total` = count(DISTINCT source) for the filter, ignoring limit/offset. */
final case class SearchResult(
    total: Int,
    hits: List[Hit],
    /** Where this page starts, and the offset of the next one (absent on the last) — [[OffsetPage]]. */
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def page: OffsetPage = OffsetPage(total, offset, hits.size)
object SearchResult:
  given Schema[SearchResult] = summon[KindlingsSchema[SearchResult]].schema
  def of(total: Int, hits: List[Hit], offset: Int): SearchResult =
    SearchResult(total, hits, offset, OffsetPage(total, offset, hits.size).next)
