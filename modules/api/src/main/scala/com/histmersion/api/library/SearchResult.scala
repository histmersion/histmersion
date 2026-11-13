package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.OffsetPage
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.RowCount
import com.histmersion.api.Offset
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.personal.UnreadPage

final case class SearchResult(
    total: RowCount,
    hits: List[Hit],
    offset: Offset = Offset(0),
    next: Option[Offset] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def page: OffsetPage = OffsetPage(total, offset, RowCount(hits.size))
object SearchResult:
  type Total = Total.T
  object Total extends Newtype[Int, Pure]

  def of(total: Total, hits: List[Hit], offset: UnreadPage.Offset): SearchResult =
    SearchResult(
      RowCount((total).value),
      hits,
      Offset((offset).value),
      OffsetPage(RowCount((total).value), Offset((offset).value), RowCount(hits.size)).next
    )
