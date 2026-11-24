package com.histmersion
package domain.personal

import com.histmersion.api.personal.{MapZoom, ViewState}
import com.histmersion.domain.{ItemKey, ItemUid}
import com.histmersion.{Latitude, Longitude, Year}
import com.histmersion.api.ItemKind
import com.histmersion.lint.{LintRule, tolerated}

/** A target as the tables' columns hold it. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final private[personal] case class TargetColumns(
    kind: ItemKind.Wire,
    key: Option[ItemKey],
    uid: Option[ItemUid],
    lat: Option[Latitude],
    lon: Option[Longitude],
    zoom: Option[MapZoom],
    from: Option[Year],
    to: Option[Year],
    view: Option[ViewState]
)

private[personal] object TargetColumns:
  def of(t: MarkTarget): TargetColumns = t match
    case MarkTarget.Item(r) =>
      TargetColumns(t.kind.wire, Some(r.key), r.uid, None, None, None, None, None, None)
    case MarkTarget.Point(lat, lon, zoom, from, to) =>
      TargetColumns(t.kind.wire, None, None, Some(lat), Some(lon), zoom, from, to, None)
    case MarkTarget.View(state, lat, lon, zoom, from, to) =>
      TargetColumns(t.kind.wire, None, None, lat, lon, zoom, from, to, state)
    case MarkTarget.Span(from, to) =>
      TargetColumns(t.kind.wire, None, None, None, None, None, Some(from), Some(to), None)
