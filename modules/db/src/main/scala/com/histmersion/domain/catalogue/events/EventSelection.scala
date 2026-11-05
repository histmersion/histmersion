package com.histmersion.domain.catalogue.events

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.library.Filter
import com.histmersion.util.database.Sql

/** Which events a list, the map's events or the leads select: those related to the filter's sources (read with the
  * gazetteer's `scope` of its places), narrowed by the event's own text (label or description), kind, historicity,
  * place and [[EventSql.Narrow]].
  */
final case class EventSelection(
    filter: Filter,
    scope: PlaceScope = PlaceScope.empty,
    text: Option[EventSelection.Text] = None,
    kind: Option[EventSelection.Kind] = None,
    historicity: Option[EventSelection.Historicity] = None,
    placeId: Option[com.histmersion.domain.catalogue.Place.Id] = None,
    narrow: EventSql.Narrow = EventSql.Narrow()
):
  /** The WHERE over `events e` with its place `ep`, the sources' part as `related` reads them. */
  def sql(related: EventSourceFilter): Sql =
    EventSql.listWhere(related, filter, scope, text, kind, historicity, placeId, narrow)

object EventSelection:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Historicity = Historicity.T
  object Historicity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
