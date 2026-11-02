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
    text: Option[String] = None,
    kind: Option[String] = None,
    historicity: Option[String] = None,
    placeId: Option[String] = None,
    narrow: EventSql.Narrow = EventSql.Narrow()
):
  /** The WHERE over `events e` with its place `ep`, the sources' part as `related` reads them. */
  def sql(related: EventSourceFilter): Sql =
    EventSql.listWhere(related, filter, scope, text, kind, historicity, placeId, narrow)
