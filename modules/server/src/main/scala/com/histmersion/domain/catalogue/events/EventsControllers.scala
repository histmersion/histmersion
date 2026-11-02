package com.histmersion.domain.catalogue.events

import com.histmersion.api.catalogue.events.EventEntry

import com.histmersion.api.LabelRename
import com.histmersion.api.LabelRenames
import com.histmersion.api.LabelRenamed
import com.histmersion.values.BundleKind
import com.histmersion.util.identity.AliasKinds

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.{
  Api,
  ApiError,
  BBox,
  EventDeleted,
  EventGeoList,
  EventGeometry,
  EventList,
  EventPatch,
  EventPut,
  EventScale,
  Filter,
  Write
}
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.places.Wikidata
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.identity.AliasKind

/** The events' operations as REST, MCP and the CLI reach them. An old event id answers as the event it was renamed to,
  * and a write to one goes to that event. `card` is what an event's page shows beside its own fields (its catalogues,
  * sources, findings, proposals, lead and the articles citing its sources); `resolve` gives a list's filter its page
  * size, `scope` the gazetteer's reading of its places; `mapView` the map's limit and outline tolerance.
  */
final class EventsControllers(
    events: EventService,
    card: EventEntry => EventEntry,
    current: (AliasKind, String) => String,
    resolve: Filter => Filter,
    scope: Filter => PlaceScope,
    mapView: () => (Int, Double),
    maintenance: WriteGate
):

  /** What only an event is narrowed by: tier, the catalogue context, the area of the map. */
  private def narrow(q: Api.EventQuery): EventSql.Narrow =
    EventSql.Narrow(q.tier.map(_.trim).filter(_.nonEmpty), q.context.getOrElse(false), q.area.flatMap(BBox.parse))

  private def selection(q: Api.EventQuery, f: Filter): EventSelection =
    EventSelection(f, scope(f), q.text, q.kind, q.historicity, q.placeId, narrow(q))

  /** `GET /api/events` — the events related to the filter's sources. */
  def list(q: Api.EventQuery): EventList =
    val f = resolve(q.filter)
    events.list(selection(q, f), f.limit, f.offset)

  /** `GET /api/events/geo` — the map's events with their scale, anchor and outline. */
  def geo(q: Api.EventQuery): EventGeoList =
    val (limit, tolerance) = mapView()
    events.geo(selection(q, q.filter), limit, tolerance)

  /** A stated scale must be `local`, `regional` or (to clear it) `auto`. */
  private def validScale(scale: Option[String]): Either[ApiError, Unit] =
    scale match
      case Some(v) if EventScale.parse(v).isEmpty =>
        Left(
          ApiError(s"scale '$v' is not ${(EventScale.all :+ EventScale.Auto).mkString(" | ")}", Some(Write.CodeInvalid))
        )
      case _ => Right(())

  /** An event's geometry is a GeoJSON geometry the store reads: WKT, a Feature or bad coordinates are a 400 naming the
    * problem, not a failed statement. Blank passes (none / clear).
    */
  private def validGeometry(g: Option[String]): Either[ApiError, Unit] =
    g.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(())
      case Some(v) =>
        EventGeometry
          .check(v)
          .flatMap(gj => events.geometryError(gj).map(m => s"geometry is not a valid GeoJSON geometry: $m").toLeft(()))
          .left
          .map(m => ApiError(m, Some(Write.CodeInvalid)))

  /** An event's Wikidata item is a `Q…` id; `""` clears it. */
  private def validWikidata(q: Option[String]): Either[ApiError, Unit] =
    q.map(_.trim) match
      case Some(v) if v.nonEmpty && !Wikidata.isQid(v) =>
        Left(ApiError(s"wikidata '$v' is not a Wikidata item id (Q followed by digits)", Some(Write.CodeInvalid)))
      case _ => Right(())

  private def now(id: String): String = current(AliasKinds.EventRecords, id)

  def get(id: String): Either[ApiError, EventEntry] =
    events.get(now(id)).map(card).toRight(ApiError(s"No event '$id'", Some(Write.CodeNotFound)))

  /** A put to an old id replaces the event it was renamed to, never creates a second one under the old id. */
  def put(caller: Caller, id: String, in: EventPut): Either[ApiError, EventEntry] =
    maintenance.guard("put_event"):
      val to = now(id)
      validScale(in.scale).flatMap(_ => validGeometry(in.geometry)).flatMap { _ =>
        events.put(to, in)
        get(to)
      }

  def patch(caller: Caller, id: String, p: EventPatch): Either[ApiError, EventEntry] =
    maintenance.guard("patch_event"):
      val to = now(id)
      validScale(p.scale)
        .flatMap(_ => validWikidata(p.wikidata))
        .flatMap(_ => validGeometry(p.geometry))
        .flatMap(_ => events.patch(to, p).flatMap(_ => get(to)))

  def delete(caller: Caller, id: String): Either[ApiError, EventDeleted] =
    maintenance.guard("delete_event"):
      val to = now(id)
      if events.delete(to) then Right(EventDeleted(to, true))
      else Left(ApiError(s"No event '$id'", Some(Write.CodeNotFound)))

  /** `POST /api/events/{id}/rename` — a new id (`to` checks the one asked for against the current one); the old one
    * keeps resolving. Answers the event's id before and after.
    */
  /** `POST /api/events/{id}/rename` — a new id; the old one keeps resolving (V053). */
  def renamed(id: String, req: LabelRename): Either[ApiError, LabelRenamed] =
    rename(Caller.Local, id, from => LabelRenames.validate(BundleKind.Event, from, req.to))
      .map((from, to) => LabelRenamed(BundleKind.Event, from, to))

  def rename(caller: Caller, id: String, to: String => Either[ApiError, String]): Either[ApiError, (String, String)] =
    maintenance.guard("rename_event"):
      val from = now(id)
      for
        newId <- to(from)
        _ <- events.rename(from, newId)
      yield (from, newId)
