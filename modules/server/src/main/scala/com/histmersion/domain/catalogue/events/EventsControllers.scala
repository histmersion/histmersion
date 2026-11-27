package com.histmersion
package domain.catalogue.events

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import io.scalaland.chimney.dsl.*
import com.histmersion.api.exchange.Bundle

import com.histmersion.api.catalogue.EventEntry

import com.histmersion.api.aliases.LabelRename
import com.histmersion.api.aliases.LabelRenames
import com.histmersion.api.aliases.LabelRenamed
import com.histmersion.api.aliases.AliasKinds

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.{Api, BBox, Write}
import com.histmersion.api.catalogue.{
  EventDeleted,
  EventGeometry,
  EventList,
  EventPatch,
  EventPut,
  EventScale,
  EventSummary
}
import com.histmersion.domain.catalogue.{Event, Place}
import com.histmersion.{Offset, PageSize}
import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.places.Wikidata
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind
import com.histmersion.api.catalogue.EventGeo
import com.histmersion.api.personal.UnreadPage
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The events' operations as REST, MCP and the CLI reach them. An old event id answers as the event it was renamed to,
  * and a write to one goes to that event. `card` is what an event's page shows beside its own fields (its catalogues,
  * sources, findings, proposals, lead and the articles citing its sources); `resolve` gives a list's filter its page
  * size, `scope` the gazetteer's reading of its places; `mapView` the map's limit and outline tolerance.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final class EventsControllers(
    events: EventService,
    card: EventEntry => EventEntry,
    current: (AliasKind, Event.Id) => Event.Id,
    resolve: Filter => Filter,
    scope: Filter => PlaceScope,
    mapView: () => (PageSize, GeoTolerance),
    maintenance: WriteGate
):

  /** What only an event is narrowed by: tier, the catalogue context, the area of the map. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def narrow(q: Api.EventQuery): EventSql.Narrow =
    EventSql.Narrow(
      q.tier.map(_.value.trim).filter(_.nonEmpty).map(EventSql.Narrow.Tier(_)),
      EventSql.Narrow.Context(q.context.fold(false)(_.value)),
      q.area.flatMap(a => BBox.parse(BBox.Text(a.value)))
    )

  /** The selection a query makes; `None` when it names a place no event can be at (an id that is no place id). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def selection(q: Api.EventQuery, f: Filter): Option[EventSelection] =
    def of(place: Option[Place.Id]) =
      EventSelection(
        f,
        scope(f),
        q.text.map(t => EventSelection.Text(t.value)),
        q.kind.map(k => EventSelection.Kind(k.value)),
        q.historicity.map(h => EventSelection.Historicity(h.value)),
        place,
        narrow(q)
      )
    q.placeId.map(_.value.trim).filter(_.nonEmpty) match
      case None    => Some(of(None))
      case Some(p) => Place.Id.option(p).map(id => of(Some(id)))

  /** `GET /api/events` — the events related to the filter's sources. */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def list(q: Api.EventQuery): EventList =
    val f = resolve(q.filter)
    selection(q, f).fold(
      EventList.of(EventList.Total(0), Nil, UnreadPage.Offset(f.offset.value))
    )(sel =>
      events.list(
        sel,
        PageSize.option(f.limit.value).getOrElse(PageSize(1)),
        Offset.option(f.offset.value).getOrElse(Offset.Start)
      )
    )

  /** `GET /api/events/geo` — the map's events with their scale, anchor and outline. */
  def geo(q: Api.EventQuery): EventGeo.List =
    val (limit, tolerance) = mapView()
    selection(q, q.filter).fold(EventGeo.List(Nil))(events.geo(_, limit, tolerance))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def invalid(m: Problem) = Api.Error(m, Some(Api.Error.Code(Write.CodeInvalid)))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def notFound(m: Problem) = Api.Error(m, Some(Api.Error.Code(Write.CodeNotFound)))

  /** A stated scale must be `local`, `regional` or (to clear it) `auto`. */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def validScale(scale: Option[EventSummary.Scale]): Either[Api.Error, Unit] =
    scale match
      case Some(v) if EventScale.parse(v).isEmpty =>
        Left(
          invalid(
            Problem.Invalid.Unknown.here(
              Problem.TypeName.of(EventSummary.Scale),
              Problem.Raw.of(v),
              EventScale.all.toVector.map(Problem.Raw.of(_)) :+ Problem.Raw(EventScale.Auto)
            )
          )
        )
      case _ => Right(())

  /** An event's geometry is a GeoJSON geometry the store reads: WKT, a Feature or bad coordinates are a 400 naming the
    * problem, not a failed statement. Blank passes (none / clear).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def validGeometry(g: Option[EventPut.Geometry]): Either[Api.Error, Unit] =
    g.map(_.value.trim).filter(_.nonEmpty) match
      case None    => Right(())
      case Some(v) =>
        EventGeometry
          .check(EventGeometry.Geojson(v))
          .flatMap(gj =>
            events
              .geometryError(EventDetailRow.Geometry(gj.value))
              .map(_.within(Problem.Field("geometry")))
              .toLeft(())
          )
          .left
          .map(invalid)

  /** An event's Wikidata item is a `Q…` id; `""` clears it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def validWikidata(q: Option[EventPatch.Wikidata]): Either[Api.Error, Unit] =
    q.map(_.value.trim) match
      case Some(v) if v.nonEmpty && !Wikidata.isQid(v) =>
        Left(
          invalid(
            Problem.Invalid.Unreadable
              .here(Problem.TypeName.of(EventPatch.Wikidata), Problem.Raw(v), Vector(Problem.Raw("Q1234")))
          )
        )
      case _ => Right(())

  private def now(id: Event.Id): Event.Id = current(AliasKinds.EventRecords, id)

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def get(id: Event.Id): Either[Api.Error, EventEntry] =
    events
      .get(now(id))
      .map(card)
      .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"$id"))))

  /** A put to an old id replaces the event it was renamed to, never creates a second one under the old id. */
  def put(caller: Caller, id: Event.Id, in: EventPut)(using Creator.New): Either[Api.Error, EventEntry] =
    maintenance.guard(WriteGate.OperationName("put_event")):
      val to = now(id)
      validScale(in.scale).flatMap(_ => validGeometry(in.geometry)).flatMap { _ =>
        events.put(to, in)
        get(to)
      }

  def patch(caller: Caller, id: Event.Id, p: EventPatch)(using Creator.New): Either[Api.Error, EventEntry] =
    maintenance.guard(WriteGate.OperationName("patch_event")):
      val to = now(id)
      validScale(p.scale)
        .flatMap(_ => validWikidata(p.wikidata))
        .flatMap(_ => validGeometry(p.geometry))
        .flatMap(_ => events.patch(to, p).flatMap(_ => get(to)))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def delete(caller: Caller, id: Event.Id): Either[Api.Error, EventDeleted] =
    maintenance.guard(WriteGate.OperationName("delete_event")):
      val to = now(id)
      if events.delete(to) then
        Right(
          EventDeleted(com.histmersion.api.catalogue.Event.Id(to.value), EventDeleted.Deleted(true))
        )
      else Left(notFound(Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"$id"))))

  /** `POST /api/events/{id}/rename` — a new id (`to` checks the one asked for against the current one); the old one
    * keeps resolving. Answers the event's id before and after.
    */
  /** `POST /api/events/{id}/rename` — a new id; the old one keeps resolving (V053). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def renamed(id: Event.Id, req: LabelRename): Either[Api.Error, LabelRenamed] =
    rename(
      Caller.Local,
      id,
      from =>
        LabelRenames
          .validate(Bundle.Kind.Event, LabelRenames.Label(from.value), LabelRenames.Label(req.to.value))
          .flatMap(l => Event.Id.parse(l.value).left.map(p => Api.Error(p, Some(Api.Error.Code(Write.CodeInvalid)))))
    ).map((from, to) => LabelRenamed(Bundle.Kind.Event, LabelRenamed.From(from.value), LabelRenamed.To(to.value)))

  def rename(
      caller: Caller,
      id: Event.Id,
      to: Event.Id => Either[Api.Error, Event.Id]
  ): Either[Api.Error, (Event.Id, Event.Id)] =
    maintenance.guard(WriteGate.OperationName("rename_event")):
      val from = now(id)
      for
        newId <- to(from)
        _ <- events.rename(from, newId)
      yield (from, newId)
