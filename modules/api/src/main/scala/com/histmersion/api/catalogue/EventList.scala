package com.histmersion
package api.catalogue
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
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventList(
    total: RowCount,
    events: List[EventSummary],
    offset: Offset = Offset(0),
    next: Option[Offset] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def page: OffsetPage = OffsetPage(total, offset, RowCount(events.size))
object EventList:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Total = Total.T
  object Total extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(total: Total, rows: List[EventSummary], offset: UnreadPage.Offset): EventList =
    EventList(
      RowCount((total).value),
      rows,
      Offset((offset).value),
      OffsetPage(RowCount((total).value), Offset((offset).value), RowCount(rows.size)).next
    )
