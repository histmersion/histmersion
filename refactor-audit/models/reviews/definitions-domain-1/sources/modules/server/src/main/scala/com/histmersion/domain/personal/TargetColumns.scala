package com.histmersion.domain.personal

import com.histmersion.api.personal.{MapZoom, ViewState}
import com.histmersion.domain.{ItemKey, ItemUid}
import com.histmersion.{Latitude, Longitude, Year}

/** A target as the tables' columns hold it. */
final private[personal] case class TargetColumns(
    kind: TargetColumns.Kind,
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
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(t: MarkTarget): TargetColumns = t match
    case MarkTarget.Item(r) =>
      TargetColumns(t.kind.wire, Some(r.key), r.uid, None, None, None, None, None, None)
    case MarkTarget.Point(lat, lon, zoom, from, to) =>
      TargetColumns(t.kind.wire, None, None, Some(lat), Some(lon), zoom, from, to, None)
    case MarkTarget.View(state, lat, lon, zoom, from, to) =>
      TargetColumns(t.kind.wire, None, None, lat, lon, zoom, from, to, state)
    case MarkTarget.Span(from, to) =>
      TargetColumns(t.kind.wire, None, None, None, None, None, Some(from), Some(to), None)
